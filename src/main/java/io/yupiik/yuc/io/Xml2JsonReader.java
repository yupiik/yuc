/*
 * Copyright (c) 2023 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.yuc.io;

import io.yupiik.fusion.json.internal.JsonStrings;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

// todo: improve this part to be a real streaming (not sure it is that relevant in real life)
public class Xml2JsonReader extends Reader {
    private final Reader delegate;

    public Xml2JsonReader(final Reader xml) {
        final var saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setNamespaceAware(true);
        saxParserFactory.setValidating(false);

        try (xml) {
            saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            final var saxParser = saxParserFactory.newSAXParser();

            final var handler = new Xml2JsonHandler();
            saxParser.parse(new InputSource(xml), handler);
            delegate = new StringReader(handler.builder.toString());
        } catch (final IOException | ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int read(final char[] cbuf, final int off, final int len) throws IOException {
        return delegate.read(cbuf, off, len);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private static class Elt {
        private final String uri;
        private final String name;
        private final String attributes;

        private Elt parent;
        private int children = 0;
        private boolean started = false;

        private Elt(final String uri, final String name, final String attributes) {
            this.uri = uri;
            this.name = name;
            this.attributes = attributes;
        }
    }

    private static class Xml2JsonHandler extends DefaultHandler {
        private final StringBuilder builder = new StringBuilder();

        private Elt elt = null;
        private StringBuilder text = null;

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            text = null;
            if (elt != null) {
                elt.children++;
            }
            final var newElt = new Elt(uri, localName, attributes.getLength() == 0 ? null : IntStream.range(0, attributes.getLength())
                    .mapToObj(i -> JsonStrings.escape(attributes.getQName(i)) + ": " + JsonStrings.escape(attributes.getValue(i)))
                    .collect(joining(", ", "\"_xml_attributes_\": {", "}")));
            newElt.parent = elt;
            elt = newElt;
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) {
            if (text == null) {
                text = new StringBuilder();
            }
            text.append(ch, start, length);
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            try {
                if (elt.children == 0) {
                    if (elt.parent != null) {
                        var current = elt.parent;
                        final var missing = new ArrayList<Elt>();
                        while (current != null) {
                            if (current.started) {
                                break;
                            }
                            missing.add(current);
                            current = current.parent;
                        }
                        if (!missing.isEmpty()) {
                            Collections.reverse(missing);
                            for (final var e : missing) {
                                if (e.parent != null) {
                                    if (e.parent.children > 1) {
                                        builder.append(',');
                                    }
                                    builder.append(JsonStrings.escape(e.name)).append(':');
                                }
                                builder.append('{');
                                appendInternalAttributes(e);
                                e.started = true;
                            }
                        }

                        if (elt.parent.children > 1) {
                            builder.append(',');
                        }
                    }

                    final String value;
                    if (text == null) {
                        value = "null";
                    } else {
                        value = JsonStrings.escape(text.toString().strip());
                    }
                    if (elt.attributes == null) {
                        builder.append(JsonStrings.escape(elt.name)).append(": ").append(value);
                    } else {
                        builder.append(JsonStrings.escape(elt.name)).append(": {");
                        appendInternalAttributes(elt);
                        builder.append(", \"_xml_value_\": ").append(value).append('}');
                    }
                    return;
                }

                if (elt.parent != null && !elt.started) {
                    builder.append(JsonStrings.escape(elt.name)).append('{');
                    appendInternalAttributes(elt);
                    elt.started = true;
                }
                builder.append('}');
            } finally {
                elt = elt.parent;
            }
        }

        private void appendInternalAttributes(final Elt element) {
            if (element.uri != null && !element.uri.isBlank()) {
                builder.append("\"_xml_namespace_\": ").append(JsonStrings.escape(element.uri));
                element.children++;
            }
            if (element.attributes != null && !element.attributes.isBlank()) {
                if (element.children > 1) {
                    builder.append(", ");
                }
                builder.append(element.attributes);
                element.children++;
            }
        }
    }
}
