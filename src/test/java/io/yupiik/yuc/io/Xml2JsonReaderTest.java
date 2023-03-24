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

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.internal.formatter.SimplePrettyFormatter;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class Xml2JsonReaderTest {
    @Test
    void payment() throws IOException {
        assertXml2Json("""
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                 xmlns:rem="http://remote.externalservices.workspaceservices.curam"
                 xmlns:xsd="http://dom.w3c.org/xsd">
                   <soapenv:Header>
                     <curam:Credentials xmlns:curam="http://www.curamsoftware.com">
                     <Username>admin</Username>
                     <Password>password</Password>
                   </curam:Credentials>
                   </soapenv:Header>
                   <soapenv:Body>
                      <rem:create>
                         <rem:xmlMessage>
                     <tns:Payments xmlns:tns="http://www.curamsoftware.com/WorkspaceServices/ExternalPayment">
                        <Payment simple="true" id="123">
                            <paymentID forceToBeObjectInJson="true">2346</paymentID>
                            <sourceSystem>TestSystem</sourceSystem>
                            <cityIndustryType>CMI9001</cityIndustryType>
                            <citizenWorkspaceAccountID>8306889512684879872
                </citizenWorkspaceAccountID>
                            <paymentAmount>48.00</paymentAmount>
                            <currency>EUR</currency>
                            <paymentMethod>CHQ</paymentMethod>
                            <paymentStatus>PRO</paymentStatus>
                            <effectiveDate>2012-01-01</effectiveDate>
                            <coverPeriodFrom>2012-01-01</coverPeriodFrom>
                            <coverPeriodTo>2012-01-01</coverPeriodTo>
                            <dueDate>2012-01-01</dueDate>
                            <payeeName>D</payeeName>
                            <payeeAddress>E</payeeAddress>
                            <paymentReferenceNo>F</paymentReferenceNo>
                            <bankSortCode>G</bankSortCode>
                            <bankAccountNo>H</bankAccountNo>
                            <PaymentBreakdown>
                                <PaymentLineItem>
                                    <caseName>I</caseName>
                                    <caseReferenceNo>J</caseReferenceNo>
                                    <componentType>C24000</componentType>
                                    <debitAmount>22.45</debitAmount>
                                    <creditAmount>49.76</creditAmount>
                                    <coverPeriodFrom>2012-01-01</coverPeriodFrom>
                                    <coverPeriodTo>2012-01-01</coverPeriodTo>
                                </PaymentLineItem>
                                <PaymentLineItem>
                                    <caseName>I</caseName>
                                    <caseReferenceNo>J</caseReferenceNo>
                                    <componentType>C24000</componentType>
                                    <debitAmount>22.45</debitAmount>
                                    <creditAmount>49.76</creditAmount>
                                    <coverPeriodFrom>2012-01-01</coverPeriodFrom>
                                    <coverPeriodTo>2012-01-01</coverPeriodTo>
                                </PaymentLineItem>
                            </PaymentBreakdown>
                        </Payment>
                    </tns:Payments>
                         </rem:xmlMessage>
                      </rem:create>
                   </soapenv:Body>
                </soapenv:Envelope>""", """
                {
                  "_xml_namespace_": "http://schemas.xmlsoap.org/soap/envelope/",
                  "Header": {
                    "_xml_namespace_": "http://schemas.xmlsoap.org/soap/envelope/",
                    "Credentials": {
                      "_xml_namespace_": "http://www.curamsoftware.com",
                      "Username": "admin",
                      "Password": "password"
                    }
                  },
                  "Body": {
                    "_xml_namespace_": "http://schemas.xmlsoap.org/soap/envelope/",
                    "create": {
                      "_xml_namespace_": "http://remote.externalservices.workspaceservices.curam",
                      "xmlMessage": {
                        "_xml_namespace_": "http://remote.externalservices.workspaceservices.curam",
                        "Payments": {
                          "_xml_namespace_": "http://www.curamsoftware.com/WorkspaceServices/ExternalPayment",
                          "Payment": {
                            "_xml_attributes_": {
                              "simple": "true",
                              "id": "123"
                            },
                            "paymentID": {
                              "_xml_attributes_": {
                                "forceToBeObjectInJson": "true"
                              },
                              "_xml_value_": "2346"
                            },
                            "sourceSystem": "TestSystem",
                            "cityIndustryType": "CMI9001",
                            "citizenWorkspaceAccountID": "8306889512684879872",
                            "paymentAmount": "48.00",
                            "currency": "EUR",
                            "paymentMethod": "CHQ",
                            "paymentStatus": "PRO",
                            "effectiveDate": "2012-01-01",
                            "coverPeriodFrom": "2012-01-01",
                            "coverPeriodTo": "2012-01-01",
                            "dueDate": "2012-01-01",
                            "payeeName": "D",
                            "payeeAddress": "E",
                            "paymentReferenceNo": "F",
                            "bankSortCode": "G",
                            "bankAccountNo": "H",
                            "PaymentBreakdown": {
                              "PaymentLineItem": {
                                "caseName": "I",
                                "caseReferenceNo": "J",
                                "componentType": "C24000",
                                "debitAmount": "22.45",
                                "creditAmount": "49.76",
                                "coverPeriodFrom": "2012-01-01",
                                "coverPeriodTo": "2012-01-01"
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }""");
    }

    private void assertXml2Json(final String xml, final String expectedJson) throws IOException {
        try (final var in = new BufferedReader(new Xml2JsonReader(new StringReader(xml)));
             final var jsonMapper = new JsonMapperImpl(List.of(), c -> Optional.empty())) {
            final var json = in.lines().collect(joining("\n"));
            try {
                assertEquals(expectedJson, new SimplePrettyFormatter(jsonMapper).apply(json));
            } catch (final IllegalStateException ise) {
                fail(json, ise);
            }
        }
    }
}
