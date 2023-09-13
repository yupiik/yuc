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

import io.yupiik.fusion.json.JsonMapper;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

// todo: improve this part to be a real streaming (not sure it is that relevant in real life)
public class Xml2JsonReader extends Reader {
    private final Reader delegate;

    public Xml2JsonReader(final Reader xml, final JsonMapper jsonMapper, final boolean autoList) {
        final var saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setNamespaceAware(true);
        saxParserFactory.setValidating(false);

        try (xml) {
            saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            final var saxParser = saxParserFactory.newSAXParser();

            final var handler = new Xml2JsonHandler(autoList);
            saxParser.parse(new InputSource(xml), handler);
            delegate = new StringReader(jsonMapper.toString(handler.object.getLast()));
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

    private static class Xml2JsonHandler extends DefaultHandler {
        private final boolean autoList;

        private StringBuilder text = new StringBuilder();
        private LinkedList<Map<String, Object>> object = new LinkedList<>();

        private Xml2JsonHandler(final boolean autoList) {
            this.autoList = autoList;
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            text = null;

            final var xmlAttributes = IntStream.range(0, attributes.getLength())
                    .boxed()
                    .collect(toMap(attributes::getQName, attributes::getValue));
            final var obj = new LinkedHashMap<String, Object>();
            if (uri != null && !uri.isBlank()) {
                obj.put("_xml_namespace_", uri);
            }
            if (!xmlAttributes.isEmpty()) {
                obj.put("_xml_attributes_", xmlAttributes);
            }

            if (!object.isEmpty()) {
                object.getLast().compute(localName, (k, v) -> {
                    if (v != null) {
                        if (autoList) {
                            return v instanceof Collection<?> c ?
                                    Stream.concat(c.stream(), Stream.of(obj)).toList() :
                                    Stream.of(v, obj).toList();
                        }
                        return v;
                    }
                    return obj;
                });
            }
            object.add(obj);
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) {
            if (text == null) {
                text = new StringBuilder();
            }
            text.append(ch, start, length);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void endElement(final String uri, final String localName, final String qName) {
            final var last = object.size() == 1;
            final var popped = last ? object.getLast() : object.removeLast();
            if (text != null) {
                final var string = text.toString().strip();
                if (!string.isEmpty()) {
                    if (popped.isEmpty()) {
                        object.getLast().compute(localName, (k, v) -> {
                            if (v instanceof List l) {
                                l.remove(l.size() - 1);
                                l.add(string);
                                return l;
                            }
                            return string;
                        });
                    } else {
                        popped.put("_xml_value_", string);
                    }
                }
                text = null;
            }

            // drop empty object == null
            if (!last && popped.isEmpty()) {
                final var value = object.getLast().get(localName);
                if (value instanceof Map<?, ?>) {
                    object.getLast().put(localName, null);
                }
            }
        }
    }
}
