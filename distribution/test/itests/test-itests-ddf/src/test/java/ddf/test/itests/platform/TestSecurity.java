/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.test.itests.platform;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.codice.ddf.itests.common.catalog.CatalogTestCommons.deleteMetacard;
import static org.codice.ddf.itests.common.catalog.CatalogTestCommons.ingest;
import static org.codice.ddf.itests.common.csw.CswTestCommons.CSW_FEDERATED_SOURCE_FACTORY_PID;
import static org.codice.ddf.itests.common.csw.CswTestCommons.getCswSourceProperties;
import static org.codice.ddf.itests.common.opensearch.OpenSearchTestCommons.OPENSEARCH_FACTORY_PID;
import static org.codice.ddf.itests.common.opensearch.OpenSearchTestCommons.getOpenSearchSourceProperties;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.authentication.CertificateAuthSettings.certAuthSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.codice.ddf.itests.common.AbstractIntegrationTest;
import org.codice.ddf.itests.common.annotations.BeforeExam;
import org.codice.ddf.security.common.jaxrs.RestSecurity;
import org.hamcrest.xml.HasXPath;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.cm.Configuration;

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;

import ddf.catalog.data.Metacard;
import ddf.security.SecurityConstants;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class TestSecurity extends AbstractIntegrationTest {

    protected static final String TRUST_STORE_PATH = System.getProperty("javax.net.ssl.trustStore");

    protected static final String KEY_STORE_PATH = System.getProperty("javax.net.ssl.keyStore");

    protected static final String PASSWORD = System.getProperty("javax.net.ssl.trustStorePassword");

    protected static final List<String> SERVICES_TO_FILTER = Arrays.asList(
            "org.codice.ddf.catalog.security.CatalogPolicy",
            "org.codice.ddf.security.policy.context.impl.PolicyManager",
            "ddf.security.pdp.realm.AuthzRealm",
            "testCreateFactoryPid",
            "org.codice.ddf.admin.config.policy.AdminConfigPolicy");

    protected static final List<String> FEATURES_TO_FILTER =
            Arrays.asList("catalog-security-plugin",
                    "security-sts-propertyclaimshandler",
                    "security-all");

    protected static final String ADD_SDK_APP_JOLOKIA_REQ =
            "{\"type\":\"EXEC\",\"mbean\":\"org.codice.ddf.admin.application.service.ApplicationService:service=application-service\",\"operation\":\"addApplications\",\"arguments\":[[{\"value\":\"mvn:ddf.distribution/sdk-app/"+System.getProperty("ddf.version")+"/xml/features\"}]]}";

    protected static final String SOAP_ENV =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
                    + "    <soap:Header>\n"
                    + "        <Action xmlns=\"http://www.w3.org/2005/08/addressing\">http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue</Action>\n"
                    + "        <MessageID xmlns=\"http://www.w3.org/2005/08/addressing\">urn:uuid:c0c43e1e-0264-4018-9a58-d1fda4332ab3</MessageID>\n"
                    + "        <To xmlns=\"http://www.w3.org/2005/08/addressing\">https://localhost:8993/services/SecurityTokenService</To>\n"
                    + "        <ReplyTo xmlns=\"http://www.w3.org/2005/08/addressing\">\n"
                    + "            <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>\n"
                    + "        </ReplyTo>\n"
                    + "        <wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\" soap:mustUnderstand=\"1\">\n"
                    + "            <wsu:Timestamp wsu:Id=\"TS-3\">\n"
                    + "                <wsu:Created>CREATED</wsu:Created>\n"
                    + "                <wsu:Expires>EXPIRES</wsu:Expires>\n"
                    + "            </wsu:Timestamp>\n" + "        </wsse:Security>\n"
                    + "    </soap:Header>\n" + "    <soap:Body>\n"
                    + "        <wst:RequestSecurityToken xmlns:wst=\"http://docs.oasis-open.org/ws-sx/ws-trust/200512\">\n"
                    + "            <wst:SecondaryParameters>\n"
                    + "                <t:TokenType xmlns:t=\"http://docs.oasis-open.org/ws-sx/ws-trust/200512\">http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV2.0</t:TokenType>\n"
                    + "                <t:KeyType xmlns:t=\"http://docs.oasis-open.org/ws-sx/ws-trust/200512\">http://docs.oasis-open.org/ws-sx/ws-trust/200512/Bearer</t:KeyType>\n"
                    + "                <t:Claims xmlns:ic=\"http://schemas.xmlsoap.org/ws/2005/05/identity\" xmlns:t=\"http://docs.oasis-open.org/ws-sx/ws-trust/200512\" Dialect=\"http://schemas.xmlsoap.org/ws/2005/05/identity\">\n"
                    + "                    <!--Add any additional claims you want to grab for the service-->\n"
                    + "                    <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role\"/>\n"
                    + "                    <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier\"/>\n"
                    + "                    <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress\"/>\n"
                    + "                    <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname\"/>\n"
                    + "                    <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname\"/>\n"
                    + "                </t:Claims>\n" + "            </wst:SecondaryParameters>\n"
                    + "                ON_BEHALF_OF"
                    + "            <wst:RequestType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue</wst:RequestType>\n"
                    + "            <wsp:AppliesTo xmlns:wsp=\"http://schemas.xmlsoap.org/ws/2004/09/policy\">\n"
                    + "                <wsa:EndpointReference xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">\n"
                    + "                    <wsa:Address>https://localhost:8993/services/QueryService</wsa:Address>\n"
                    + "                </wsa:EndpointReference>\n"
                    + "            </wsp:AppliesTo>\n" + "            <wst:Renewing/>\n"
                    + "        </wst:RequestSecurityToken>\n" + "    </soap:Body>\n"
                    + "</soap:Envelope>";

    protected static final String SDK_SOAP_CONTEXT = "/services/sdk";

    private static final String BAD_X509_TOKEN =
            "                        MIIDQDCCAqmgAwIBAgICAQUwDQYJKoZIhvcNAQEFBQAwTjELMAkGA1UEBhMCSlAxETAPBg\n"
                    + "    NVBAgTCEthbmFnYXdhMQwwCgYDVQQKEwNJQk0xDDAKBgNVBAsTA1RSTDEQMA4GA1UEAxMH\n"
                    + "    SW50IENBMjAeFw0wMTEwMDExMDAwMzlaFw0xMTEwMDExMDAwMzlaMFMxCzAJBgNVBAYTAk\n"
                    + "    pQMREwDwYDVQQIEwhLYW5hZ2F3YTEMMAoGA1UEChMDSUJNMQwwCgYDVQQLEwNUUkwxFTAT\n"
                    + "    BgNVBAMTDFNPQVBQcm92aWRlcjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAraakNJ\n"
                    + "    1JzkPUuvPdXRvPOOCl12nBwmqvt65dk/x+QzxxarDNwH+eWRbLyyKcrAyd0XGV+Zbvj6V3\n"
                    + "    O9DSVCZUCJttw6bbqqeYhwAP3V8s24sID77tk3gOhUTEGYxsljX2orL26SLqFJMrvnvk2F\n"
                    + "    RS2mrdkZEBUG97mD4QWcln4d0CAwEAAaOCASYwggEiMAkGA1UdEwQCMAAwCwYDVR0PBAQD\n"
                    + "    AgXgMCwGCWCGSAGG+EIBDQQfFh1PcGVuU1NMIEdlbmVyYXRlZCBDZXJ0aWZpY2F0ZTAdBg\n"
                    + "    NVHQ4EFgQUlXSsrVRfZOLGdJdjEIwTbuSTe4UwgboGA1UdIwSBsjCBr4AUvfkg1Tj5ZHLT\n"
                    + "    29p/3M6w/tC872+hgZKkgY8wgYwxCzAJBgNVBAYTAkpQMREwDwYDVQQIEwhLYW5hZ2F3YT\n"
                    + "    EPMA0GA1UEBxMGWWFtYXRvMQwwCgYDVQQKEwNJQk0xDDAKBgNVBAsTA1RSTDEZMBcGA1UE\n"
                    + "    AxMQU09BUCAyLjEgVGVzdCBDQTEiMCAGCSqGSIb3DQEJARYTbWFydXlhbWFAanAuaWJtLm\n"
                    + "    NvbYICAQEwDQYJKoZIhvcNAQEFBQADgYEAXE7mE1RPb3lYAYJFzBb3VAHvkCWa/HQtCOZd\n"
                    + "    yniCHp3MJ9EbNTq+QpOHV60YE8u0+5SejCzFSOHOpyBgLPjWoz8JXQnjV7VcAbTglw+ZoO\n"
                    + "    SYy64rfhRdr9giSs47F4D6woPsAd2ubg/YhMaXLTSyGxPdV3VqQsutuSgDUDoqWCA=";

    private static final String GOOD_X509_TOKEN =
            "MIIC8DCCAlmgAwIBAgIJAIzc4FYrIp9pMA0GCSqGSIb3DQEBCwUAMIGEMQswCQYD\n"
                    + "VQQGEwJVUzELMAkGA1UECBMCQVoxDDAKBgNVBAoTA0RERjEMMAoGA1UECxMDRGV2\n"
                    + "MRkwFwYDVQQDExBEREYgRGVtbyBSb290IENBMTEwLwYJKoZIhvcNAQkBFiJlbWFp\n"
                    + "bEFkZHJlc3M9ZGRmcm9vdGNhQGV4YW1wbGUub3JnMCAXDTE1MTIxMTE1NDMyM1oY\n"
                    + "DzIxMTUxMTE3MTU0MzIzWjBwMQswCQYDVQQGEwJVUzELMAkGA1UECBMCQVoxDDAK\n"
                    + "BgNVBAoTA0RERjEMMAoGA1UECxMDRGV2MRIwEAYDVQQDEwlsb2NhbGhvc3QxJDAi\n"
                    + "BgkqhkiG9w0BCQEWFWxvY2FsaG9zdEBleGFtcGxlLm9yZzCBnzANBgkqhkiG9w0B\n"
                    + "AQEFAAOBjQAwgYkCgYEAx4LI1lsJNmmEdB8HmDwWuAGrVFjNXuKRXD+lUaTPyDHe\n"
                    + "XcD32zxa0DiZEB5vqfS9NH3I0E56Rbidg6IQ6r/9hOL9+sjWTPRBsQfWzZwjmcUG\n"
                    + "61psPc9gbFRK5qltz4BLv4+SWvRMMjgxHM8+SROnjCU5FD9roJ9Ww2v+ZWAvYJ8C\n"
                    + "AwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgBhvhCAQ0EHxYdT3BlblNTTCBHZW5l\n"
                    + "cmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYEFID3lAgzIEAdGx3RHizsLcGt4Wuw\n"
                    + "MB8GA1UdIwQYMBaAFOFUx5ffCsK/qV94XjsLK+RIF73GMA0GCSqGSIb3DQEBCwUA\n"
                    + "A4GBACWWsi4WusO5/u1O91obGn8ctFnxVlogBQ/tDZ+neQDxy8YB2J28tztELrRH\n"
                    + "kaGiCPT4CCKdy0hx/bG/jSM1ypJnPKrPVrCkYL3Y68pzxvrFNq5NqAFCcBOCNsDN\n"
                    + "fvCSZ/XHvFyGHIuso5wNVxJyvTdhQ+vWbnpiX8qr6vTx2Wgw";

    private static final String GOOD_X509_PATH_TOKEN =
            "MIIC9DCCAvAwggJZoAMCAQICCQCM3OBWKyKfaTANBgkqhkiG9w0BAQsFADCBhDELMAkGA1UEBhMCVVMxCzAJBgNVBAgTAkFaMQwwCgYDVQQKEwNEREYxDDAKBgNVBAsTA0RldjEZMBcGA1UEAxMQRERGIERlbW8gUm9vdCBDQTExMC8GCSqGSIb3DQEJARYiZW1haWxBZGRyZXNzPWRkZnJvb3RjYUBleGFtcGxlLm9yZzAgFw0xNTEyMTExNTQzMjNaGA8yMTE1MTExNzE1NDMyM1owcDELMAkGA1UEBhMCVVMxCzAJBgNVBAgTAkFaMQwwCgYDVQQKEwNEREYxDDAKBgNVBAsTA0RldjESMBAGA1UEAxMJbG9jYWxob3N0MSQwIgYJKoZIhvcNAQkBFhVsb2NhbGhvc3RAZXhhbXBsZS5vcmcwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMeCyNZbCTZphHQfB5g8FrgBq1RYzV7ikVw/pVGkz8gx3l3A99s8WtA4mRAeb6n0vTR9yNBOekW4nYOiEOq//YTi/frI1kz0QbEH1s2cI5nFButabD3PYGxUSuapbc+AS7+Pklr0TDI4MRzPPkkTp4wlORQ/a6CfVsNr/mVgL2CfAgMBAAGjezB5MAkGA1UdEwQCMAAwLAYJYIZIAYb4QgENBB8WHU9wZW5TU0wgR2VuZXJhdGVkIENlcnRpZmljYXRlMB0GA1UdDgQWBBSA95QIMyBAHRsd0R4s7C3BreFrsDAfBgNVHSMEGDAWgBThVMeX3wrCv6lfeF47CyvkSBe9xjANBgkqhkiG9w0BAQsFAAOBgQAllrIuFrrDuf7tTvdaGxp/HLRZ8VZaIAUP7Q2fp3kA8cvGAdidvLc7RC60R5Ghogj0+AginctIcf2xv40jNcqSZzyqz1awpGC92OvKc8b6xTauTagBQnATgjbAzX7wkmf1x7xchhyLrKOcDVcScr03YUPr1m56Yl/Kq+r08dloMA==";

    private static final String OPENSEARCH_SAML_SOURCE_ID = "openSearchSamlSource";

    private static final DynamicUrl SECURE_ROOT_AND_PORT = new DynamicUrl(DynamicUrl.SECURE_ROOT,
            HTTPS_PORT);

    private static final DynamicUrl ADMIN_PATH = new DynamicUrl(SECURE_ROOT_AND_PORT,
            "/admin/index.html");

    //this uses a cert that won't be sent by the TLS connection
    private static final String BAD_HOK_EXAMPLE =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
                    + "   <soap:Header>\n"
                    + "        <Action xmlns=\"http://www.w3.org/2005/08/addressing\">http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue</Action>\n"
                    + "        <MessageID xmlns=\"http://www.w3.org/2005/08/addressing\">urn:uuid:c0c43e1e-0264-4018-9a58-d1fda4332ab3</MessageID>\n"
                    + "        <To xmlns=\"http://www.w3.org/2005/08/addressing\">https://localhost:8993/services/SecurityTokenService</To>\n"
                    + "        <ReplyTo xmlns=\"http://www.w3.org/2005/08/addressing\">\n"
                    + "            <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>\n"
                    + "        </ReplyTo>\n"
                    + "      <wsse:Security soap:mustUnderstand=\"1\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n"
                    + "         <wsu:Timestamp wsu:Id=\"TS-EF182B133DACAE158E14503737766347\">\n"
                    + "            <wsu:Created>CREATED</wsu:Created>\n"
                    + "            <wsu:Expires>EXPIRES</wsu:Expires>\n"
                    + "         </wsu:Timestamp>\n" + "      </wsse:Security>\n"
                    + "   </soap:Header>\n" + "   <soap:Body>\n"
                    + "      <wst:RequestSecurityToken xmlns:wst=\"http://docs.oasis-open.org/ws-sx/ws-trust/200512\">\n"
                    + "         <wst:RequestType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue</wst:RequestType>\n"
                    + "         <wsp:AppliesTo xmlns:wsp=\"http://schemas.xmlsoap.org/ws/2004/09/policy\">\n"
                    + "            <wsa:EndpointReference xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">\n"
                    + "               <wsa:Address>https://localhost:8993/services/SecurityTokenService</wsa:Address>\n"
                    + "            </wsa:EndpointReference>\n" + "         </wsp:AppliesTo>\n"
                    + "         <wst:Claims Dialect=\"http://schemas.xmlsoap.org/ws/2005/05/identity\" xmlns:ic=\"http://schemas.xmlsoap.org/ws/2005/05/identity\">\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier\"/>\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress\"/>\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname\"/>\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname\"/>\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role\"/>\n"
                    + "         </wst:Claims>\n" + "         <wst:OnBehalfOf>\n"
                    + "            <wsse:UsernameToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">\n"
                    + "               <wsse:Username>admin</wsse:Username>\n"
                    + "               <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">admin</wsse:Password>\n"
                    + "            </wsse:UsernameToken>\n" + "         </wst:OnBehalfOf>\n"
                    + "         <wst:TokenType>http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV2.0</wst:TokenType>\n"
                    + "         <wst:KeyType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/PublicKey</wst:KeyType>\n"
                    + "         <wst:UseKey>\n"
                    + "            <ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n"
                    + "               <ds:X509Data>\n"
                    + "                  <ds:X509Certificate>MIIDcDCCAtmgAwIBAgIJAIzc4FYrIp9qMA0GCSqGSIb3DQEBCwUAMIGEMQswCQYD\n"
                    + "VQQGEwJVUzELMAkGA1UECBMCQVoxDDAKBgNVBAoTA0RERjEMMAoGA1UECxMDRGV2\n"
                    + "MRkwFwYDVQQDExBEREYgRGVtbyBSb290IENBMTEwLwYJKoZIhvcNAQkBFiJlbWFp\n"
                    + "bEFkZHJlc3M9ZGRmcm9vdGNhQGV4YW1wbGUub3JnMCAXDTE1MTIxNzE3MzUzMloY\n"
                    + "DzIxMTUxMTIzMTczNTMyWjBsMQswCQYDVQQGEwJVUzELMAkGA1UECBMCQVoxDDAK\n"
                    + "BgNVBAoTA0RERjEMMAoGA1UECxMDRGV2MRAwDgYDVQQDEwdleGFtcGxlMSIwIAYJ\n"
                    + "KoZIhvcNAQkBFhNleGFtcGxlQGV4YW1wbGUub3JnMIIBIjANBgkqhkiG9w0BAQEF\n"
                    + "AAOCAQ8AMIIBCgKCAQEAoMoUxCQOxA8INQ1NQQcd4k/pwraU+x58ymGJPWeT+SCA\n"
                    + "OiD4xJs3qzqC4Ex9tztxUhGyAH56YYaZCtVrJxejYUPbXYRBLuU2ecw3adWJyk2f\n"
                    + "fL+hyc4eDa640KQ8+W0dz2hI1OPSsI1KzRdaYbe8f1GcWL8TshOZ+o0fC036GOsi\n"
                    + "szCnqXaQZbObddEMGHWMEPJzToIEUrt/+t3eAeNNF9A/jjhELJrzgaWqJNuEcC3q\n"
                    + "gfgdeF/itjurRjIkmBDs4VkplUX+JWFPF78pyYcbLEle1dV1ZxZIZv7vFlZYjZn2\n"
                    + "Qacf+iLQnk3m+tGCtA2Q8DKWCFl/fGtJPoIyHQsmswIDAQABo3sweTAJBgNVHRME\n"
                    + "AjAAMCwGCWCGSAGG+EIBDQQfFh1PcGVuU1NMIEdlbmVyYXRlZCBDZXJ0aWZpY2F0\n"
                    + "ZTAdBgNVHQ4EFgQUBicdpPA//+rQjR/DJwD/beoIwREwHwYDVR0jBBgwFoAU4VTH\n"
                    + "l98Kwr+pX3heOwsr5EgXvcYwDQYJKoZIhvcNAQELBQADgYEALUz4LJAtaGfRpEuC\n"
                    + "VtjdpQT1E2gL0PXyBgR5jchBVzvHckectvaUh+rHbwATh1jahbk/0/0J53NMEi49\n"
                    + "TOuYQtmHtiMvl1oBqAke1mJgDPgoGE9T3wWM4FcnA8z7LpBJeo661mchRge+vyW/\n"
                    + "kVCd/oPtz1DRhKttYBa6LB7gswk=</ds:X509Certificate>\n"
                    + "               </ds:X509Data>\n" + "            </ds:KeyInfo>\n"
                    + "         </wst:UseKey>\n" + "         <wst:Renewing/>\n"
                    + "      </wst:RequestSecurityToken>\n" + "   </soap:Body>\n"
                    + "</soap:Envelope>";

    //this uses the default localhost cert which will be in the TLS connection
    private static final String GOOD_HOK_EXAMPLE =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
                    + "   <soap:Header>\n"
                    + "        <Action xmlns=\"http://www.w3.org/2005/08/addressing\">http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue</Action>\n"
                    + "        <MessageID xmlns=\"http://www.w3.org/2005/08/addressing\">urn:uuid:c0c43e1e-0264-4018-9a58-d1fda4332ab3</MessageID>\n"
                    + "        <To xmlns=\"http://www.w3.org/2005/08/addressing\">https://localhost:8993/services/SecurityTokenService</To>\n"
                    + "        <ReplyTo xmlns=\"http://www.w3.org/2005/08/addressing\">\n"
                    + "            <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>\n"
                    + "        </ReplyTo>\n"
                    + "      <wsse:Security soap:mustUnderstand=\"1\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">\n"
                    + "         <wsu:Timestamp wsu:Id=\"TS-EF182B133DACAE158E14503728805225\">\n"
                    + "            <wsu:Created>CREATED</wsu:Created>\n"
                    + "            <wsu:Expires>EXPIRES</wsu:Expires>\n"
                    + "         </wsu:Timestamp>\n" + "      </wsse:Security>\n"
                    + "   </soap:Header>\n" + "   <soap:Body>\n"
                    + "      <wst:RequestSecurityToken xmlns:wst=\"http://docs.oasis-open.org/ws-sx/ws-trust/200512\">\n"
                    + "         <wst:RequestType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue</wst:RequestType>\n"
                    + "         <wsp:AppliesTo xmlns:wsp=\"http://schemas.xmlsoap.org/ws/2004/09/policy\">\n"
                    + "            <wsa:EndpointReference xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">\n"
                    + "               <wsa:Address>https://localhost:8993/services/SecurityTokenService</wsa:Address>\n"
                    + "            </wsa:EndpointReference>\n" + "         </wsp:AppliesTo>\n"
                    + "         <wst:Claims Dialect=\"http://schemas.xmlsoap.org/ws/2005/05/identity\" xmlns:ic=\"http://schemas.xmlsoap.org/ws/2005/05/identity\">\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier\"/>\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress\"/>\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname\"/>\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname\"/>\n"
                    + "            <ic:ClaimType Optional=\"true\" Uri=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role\"/>\n"
                    + "         </wst:Claims>\n" + "         <wst:OnBehalfOf>\n"
                    + "            <wsse:UsernameToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">\n"
                    + "               <wsse:Username>admin</wsse:Username>\n"
                    + "               <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">admin</wsse:Password>\n"
                    + "            </wsse:UsernameToken>\n" + "         </wst:OnBehalfOf>\n"
                    + "         <wst:TokenType>http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV2.0</wst:TokenType>\n"
                    + "         <wst:KeyType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/PublicKey</wst:KeyType>\n"
                    + "         <wst:UseKey>\n"
                    + "            <ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n"
                    + "               <ds:X509Data>\n"
                    + "                  <ds:X509Certificate>MIIC8DCCAlmgAwIBAgIJAIzc4FYrIp9pMA0GCSqGSIb3DQEBCwUAMIGEMQswCQYD\n"
                    + "VQQGEwJVUzELMAkGA1UECBMCQVoxDDAKBgNVBAoTA0RERjEMMAoGA1UECxMDRGV2\n"
                    + "MRkwFwYDVQQDExBEREYgRGVtbyBSb290IENBMTEwLwYJKoZIhvcNAQkBFiJlbWFp\n"
                    + "bEFkZHJlc3M9ZGRmcm9vdGNhQGV4YW1wbGUub3JnMCAXDTE1MTIxMTE1NDMyM1oY\n"
                    + "DzIxMTUxMTE3MTU0MzIzWjBwMQswCQYDVQQGEwJVUzELMAkGA1UECBMCQVoxDDAK\n"
                    + "BgNVBAoTA0RERjEMMAoGA1UECxMDRGV2MRIwEAYDVQQDEwlsb2NhbGhvc3QxJDAi\n"
                    + "BgkqhkiG9w0BCQEWFWxvY2FsaG9zdEBleGFtcGxlLm9yZzCBnzANBgkqhkiG9w0B\n"
                    + "AQEFAAOBjQAwgYkCgYEAx4LI1lsJNmmEdB8HmDwWuAGrVFjNXuKRXD+lUaTPyDHe\n"
                    + "XcD32zxa0DiZEB5vqfS9NH3I0E56Rbidg6IQ6r/9hOL9+sjWTPRBsQfWzZwjmcUG\n"
                    + "61psPc9gbFRK5qltz4BLv4+SWvRMMjgxHM8+SROnjCU5FD9roJ9Ww2v+ZWAvYJ8C\n"
                    + "AwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgBhvhCAQ0EHxYdT3BlblNTTCBHZW5l\n"
                    + "cmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYEFID3lAgzIEAdGx3RHizsLcGt4Wuw\n"
                    + "MB8GA1UdIwQYMBaAFOFUx5ffCsK/qV94XjsLK+RIF73GMA0GCSqGSIb3DQEBCwUA\n"
                    + "A4GBACWWsi4WusO5/u1O91obGn8ctFnxVlogBQ/tDZ+neQDxy8YB2J28tztELrRH\n"
                    + "kaGiCPT4CCKdy0hx/bG/jSM1ypJnPKrPVrCkYL3Y68pzxvrFNq5NqAFCcBOCNsDN\n"
                    + "fvCSZ/XHvFyGHIuso5wNVxJyvTdhQ+vWbnpiX8qr6vTx2Wgw</ds:X509Certificate>\n"
                    + "               </ds:X509Data>\n" + "            </ds:KeyInfo>\n"
                    + "         </wst:UseKey>\n" + "         <wst:Renewing/>\n"
                    + "      </wst:RequestSecurityToken>\n" + "   </soap:Body>\n"
                    + "</soap:Envelope>";

    public static final String SAMPLE_SOAP =
            "<?xml version=\"1.0\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><helloWorld xmlns=\"http://ddf.sdk/soap/hello\" /></soap:Body></soap:Envelope>";

    @BeforeExam
    public void beforeTest() throws Exception {
        try {
            basePort = getBasePort();
            getAdminConfig().setLogLevels();
            getServiceManager().waitForRequiredApps(getDefaultRequiredApps());

            getServiceManager().waitForAllBundles();
            configurePDP();
            Configuration config = getAdminConfig().getConfiguration(
                    "org.codice.ddf.admin.config.policy.AdminConfigPolicy");
            config.setBundleLocation("mvn:ddf.admin.core/admin-core-configpolicy/" + System.getProperty("ddf.version"));
            Dictionary properties = new Hashtable<>();

            List<String> featurePolicies = new ArrayList<>();
            featurePolicies.addAll(Arrays.asList(getDefaultRequiredApps()));
            featurePolicies.addAll(FEATURES_TO_FILTER);
            featurePolicies.replaceAll(featureName -> featureName
                    + "=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role=admin\"");

            List<String> servicePolicies = new ArrayList<>();
            servicePolicies.addAll(SERVICES_TO_FILTER);
            servicePolicies.replaceAll(serviceName -> serviceName
                    + "=\"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role=admin\"");

            properties.put("featurePolicies",
                    featurePolicies.stream()
                            .toArray(String[]::new));
            properties.put("servicePolicies",
                    servicePolicies.stream()
                            .toArray(String[]::new));
            config.update(properties);

            getServiceManager().waitForHttpEndpoint(SERVICE_ROOT + "/catalog/query");
            getCatalogBundle().waitForCatalogProvider();
        } catch (Exception e) {
            LOGGER.error("Failed in @BeforeExam: ", e);
            fail("Failed in @BeforeExam: " + e.getMessage());
        }
    }

    public void configurePDP() throws Exception {
    }

    @Before
    public void before() throws Exception {
        configureRestForGuest();
    }

    private void configureRestForGuest() throws Exception {
        getSecurityPolicy().configureRestForGuest(SDK_SOAP_CONTEXT);
    }

    private void configureRestForBasic() throws Exception {
        getSecurityPolicy().configureRestForBasic(SDK_SOAP_CONTEXT);
    }

    @Test
    public void testGuestRestAccess() throws Exception {
        String url = SERVICE_ROOT.getUrl() + "/catalog/query?q=*&src=local";
        waitForSecurityHandlers(url);

        //test that guest works and check that we get an sso token
        String cookie = when().get(url)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .assertThat()
                .header("Set-Cookie", containsString("JSESSIONID"))
                .extract()
                .cookie("JSESSIONID");

        //try again with the sso token
        given().cookie("JSESSIONID", cookie)
                .when()
                .get(url)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200));

        //try to hit an admin restricted page and see that we are unauthorized
        given().cookie("JSESSIONID", cookie)
                .when()
                .get(ADMIN_PATH.getUrl())
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(403));
    }

    @Test
    public void testBasicRestAccess() throws Exception {
        String url = SERVICE_ROOT.getUrl() + "/catalog/query?q=*&src=local";

        configureRestForBasic();

        waitForSecurityHandlers(url);

        //test that we get a 401 if no credentials are specified
        when().get(url)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(401));

        //try a random user and get a 401
        given().auth()
                .basic("bad", "user")
                .when()
                .get(url)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(401));

        //try a real user and get an sso token back
        String cookie = given().auth()
                .basic("admin", "admin")
                .when()
                .get(url)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .assertThat()
                .header("Set-Cookie", containsString("JSESSIONID"))
                .extract()
                .cookie("JSESSIONID");

        //try the sso token instead of basic auth
        given().cookie("JSESSIONID", cookie)
                .when()
                .get(url)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200));

        //try that admin level sso token on a restricted resource and get in... sso works!
        given().cookie("JSESSIONID", cookie)
                .when()
                .get(ADMIN_PATH.getUrl())
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200));
    }

    @Test
    public void testSamlFederatedAuth() throws Exception {
        String recordId = ingest(getFileContent(JSON_RECORD_RESOURCE_PATH + "/SimpleGeoJsonRecord"), "application/json");
        configureRestForBasic();

        // Creating a new OpenSearch source with no username/password.
        // When an OpenSearch source attempts to authenticate without a username/password it will
        // use the subject in the request to create a SAML authentication token
        Map<String, Object> openSearchProperties = getOpenSearchSourceProperties(
                OPENSEARCH_SAML_SOURCE_ID, OPENSEARCH_PATH.getUrl(), getServiceManager());
        getServiceManager().createManagedService(OPENSEARCH_FACTORY_PID,
                openSearchProperties);

        getCatalogBundle().waitForFederatedSource(OPENSEARCH_SAML_SOURCE_ID);

        String openSearchQuery =
                SERVICE_ROOT.getUrl() + "/catalog/query?q=*&src=" + OPENSEARCH_SAML_SOURCE_ID;
        waitForSecurityHandlers(openSearchQuery);
        given().auth()
                .basic("admin", "admin")
                .when()
                .get(openSearchQuery)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .assertThat()
                .body(hasXPath("//metacard/string[@name='" + Metacard.TITLE
                        + "']/value[text()='myTitle']"));

        configureRestForGuest();
        deleteMetacard(recordId);
    }

    @Test
    public void testBasicFederatedAuth() throws Exception {
        String recordId = ingest(getFileContent(JSON_RECORD_RESOURCE_PATH + "/SimpleGeoJsonRecord"), "application/json");
        configureRestForBasic();

        //Positive tests
        Map<String, Object> openSearchProperties = getOpenSearchSourceProperties(
                OPENSEARCH_SOURCE_ID, OPENSEARCH_PATH.getUrl(), getServiceManager());
        openSearchProperties.put("username", "admin");
        openSearchProperties.put("password", "admin");
        getServiceManager().createManagedService(OPENSEARCH_FACTORY_PID,
                openSearchProperties);

        Map<String, Object> cswProperties = getCswSourceProperties(CSW_SOURCE_ID, CSW_PATH.getUrl(), getServiceManager());
        cswProperties.put("username", "admin");
        cswProperties.put("password", "admin");
        getServiceManager().createManagedService(CSW_FEDERATED_SOURCE_FACTORY_PID, cswProperties);

        getCatalogBundle().waitForFederatedSource(OPENSEARCH_SOURCE_ID);
        getCatalogBundle().waitForFederatedSource(CSW_SOURCE_ID);

        String openSearchQuery =
                SERVICE_ROOT.getUrl() + "/catalog/query?q=*&src=" + OPENSEARCH_SOURCE_ID;
        waitForSecurityHandlers(openSearchQuery);
        given().auth()
                .basic("admin", "admin")
                .when()
                .get(openSearchQuery)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .assertThat()
                .body(hasXPath("//metacard/string[@name='" + Metacard.TITLE
                        + "']/value[text()='myTitle']"));

        String cswQuery = SERVICE_ROOT.getUrl() + "/catalog/query?q=*&src=" + CSW_SOURCE_ID;
        given().auth()
                .basic("admin", "admin")
                .when()
                .get(cswQuery)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .assertThat()
                .body(hasXPath("//metacard/string[@name='" + Metacard.TITLE
                        + "']/value[text()='myTitle']"));

        //Negative tests
        String unavailableCswSourceId = "Unavailable Csw";
        cswProperties = getCswSourceProperties(unavailableCswSourceId, CSW_PATH.getUrl(), getServiceManager());
        cswProperties.put("username", "bad");
        cswProperties.put("password", "auth");
        getServiceManager().createManagedService(CSW_FEDERATED_SOURCE_FACTORY_PID, cswProperties);

        String cswQueryUnavail =
                SERVICE_ROOT.getUrl() + "/catalog/query?q=*&src=" + unavailableCswSourceId;
        given().auth()
                .basic("admin", "admin")
                .when()
                .get(cswQueryUnavail)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(500));

        /* TODO - opensearch source is throwing an exception that gets swallowed an causes this test to hang forever
        String unavailableOpenSourceId = "Unavailable OpenSearchSource";

        OpenSearchSourceProperties openSearchUnavailProp = new OpenSearchSourceProperties(
                unavailableOpenSourceId);
        openSearchUnavailProp.put("username", "bad");
        openSearchUnavailProp.put("password", "auth");
        getServiceManager().createManagedService(OpenSearchSourceProperties.FACTORY_PID,
                openSearchUnavailProp);

        String unavailableOpenSearchQuery =
                SERVICE_ROOT.getUrl() + "/catalog/query?q=*&src=" + unavailableOpenSourceId;

        given().auth()
                .basic("admin", "admin")
                .when()
                .get(unavailableOpenSearchQuery)
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .assertThat()
                .body(not(hasXPath("//metacard/string[@name='" + Metacard.TITLE
                        + "']/value[text()='myTitle']")));
                        */

        configureRestForGuest();
        deleteMetacard(recordId);
    }

    @Test
    public void testGuestSoapAccess() throws Exception {
        String body =
                "<soapenv:Envelope xmlns:hel=\"http://ddf.sdk/soap/hello\" xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
                        + "   <soapenv:Header>\n" + "   </soapenv:Header>\n" + "   <soapenv:Body>\n"
                        + "      <hel:helloWorld/>\n" + "   </soapenv:Body>\n"
                        + "</soapenv:Envelope>";
        //we are only testing guest because that hits the most code, testing with an assertion would be mostly testing the same stuff that this is hitting
        given().log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "helloWorld")
                .expect()
                .statusCode(equalTo(200))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/sdk/SoapTransportService")
                .then()
                .log()
                .all()
                .assertThat()
                .body(HasXPath.hasXPath("//*[local-name()='helloWorldResponse']/result/text()",
                        containsString("Guest")));
    }

    @Test
    public void testGuestSoapAccessHttp() throws Exception {
        getServiceManager().startFeature(true, "platform-http-proxy");

        String body =
                "<soapenv:Envelope xmlns:hel=\"http://ddf.sdk/soap/hello\" xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
                        + "   <soapenv:Header>\n" + "   </soapenv:Header>\n" + "   <soapenv:Body>\n"
                        + "      <hel:helloWorld/>\n" + "   </soapenv:Body>\n"
                        + "</soapenv:Envelope>";
        //we are only testing guest because that hits the most code, testing with an assertion would be mostly testing the same stuff that this is hitting
        given().log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "helloWorld")
                .expect()
                .statusCode(equalTo(200))
                .when()
                .post(INSECURE_SERVICE_ROOT.getUrl() + "/sdk/SoapTransportService")
                .then()
                .log()
                .all()
                .assertThat()
                .body(HasXPath.hasXPath("//*[local-name()='helloWorldResponse']/result/text()",
                        containsString("Guest")));

        getServiceManager().stopFeature(false, "platform-http-proxy");
    }

    /* These STS tests are here to prove out functionality that doesn't get hit when accessing internal services. The standard UsernameToken and BinarySecurityToken elements are supported
     * by DDF, but not used internally. These elements need to be checked for functionality independently since going through our REST security framework won't touch these validators. */

    @Test
    public void testUsernameTokenSTS() throws Exception {
        String onBehalfOf = "<wst:OnBehalfOf>"
                + "                    <wsse:UsernameToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">\n"
                + "                        <wsse:Username>admin</wsse:Username>\n"
                + "                        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">admin</wsse:Password>\n"
                + "                   </wsse:UsernameToken>\n"
                + "                </wst:OnBehalfOf>\n";
        String body = getSoapEnvelope(onBehalfOf);

        given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(200))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .log()
                .all()
                .assertThat()
                .body(HasXPath.hasXPath("//*[local-name()='Assertion']"));
    }

    @Test
    public void testBadUsernameTokenSTS() throws Exception {
        String onBehalfOf = "<wst:OnBehalfOf>"
                + "                    <wsse:UsernameToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">\n"
                + "                        <wsse:Username>admin</wsse:Username>\n"
                + "                        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">blah</wsse:Password>\n"
                + "                   </wsse:UsernameToken>\n"
                + "                </wst:OnBehalfOf>\n";
        String body = getSoapEnvelope(onBehalfOf);

        given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .
                        log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(500))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .log()
                .all();
    }

    @Test
    public void testBadX509TokenSTS() throws Exception {
        String onBehalfOf = "<wst:OnBehalfOf>\n"
                + "                    <wsse:BinarySecurityToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\" ValueType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3\" >\n"
                + BAD_X509_TOKEN + "                   </wsse:BinarySecurityToken>\n"
                + "                </wst:OnBehalfOf>\n";
        String body = getSoapEnvelope(onBehalfOf);

        given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .
                        log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(500))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .log()
                .all();

    }

    @Test
    public void testX509TokenSTS() throws Exception {
        String onBehalfOf = "<wst:OnBehalfOf>\n"
                + "<wsse:BinarySecurityToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" "
                + "EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\" "
                + "ValueType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3\" >"
                + GOOD_X509_TOKEN + "</wsse:BinarySecurityToken>\n" + "</wst:OnBehalfOf>\n";
        String body = getSoapEnvelope(onBehalfOf);

        given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(200))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .log()
                .all()
                .assertThat()
                .body(HasXPath.hasXPath("//*[local-name()='Assertion']"));

    }

    @Test
    public void testX509PathSTS() throws Exception {
        String onBehalfOf = "<wst:OnBehalfOf>\n"
                + "<wsse:BinarySecurityToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" "
                + "EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\" "
                + "ValueType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509PKIPathv1\" >"
                + GOOD_X509_PATH_TOKEN + "</wsse:BinarySecurityToken>\n" + "</wst:OnBehalfOf>\n";
        String body = getSoapEnvelope(onBehalfOf);

        given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(200))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .log()
                .all()
                .assertThat()
                .body(HasXPath.hasXPath("//*[local-name()='Assertion']"));

    }

    @Test
    public void testX509TokenWithPathSTS() throws Exception {
        String onBehalfOf = "<wst:OnBehalfOf>\n"
                + "<wsse:BinarySecurityToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" "
                + "EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\" "
                + "ValueType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3\" >\n"
                + GOOD_X509_PATH_TOKEN + "</wsse:BinarySecurityToken>\n" + "</wst:OnBehalfOf>\n";
        String body = getSoapEnvelope(onBehalfOf);

        given().log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(500))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .log()
                .all();

    }

    @Test
    public void testX509PathWithTokenSTS() throws Exception {
        String onBehalfOf = "<wst:OnBehalfOf>\n"
                + "<wsse:BinarySecurityToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" "
                + "EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\" "
                + "ValueType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509PKIPathv1\" >"
                + GOOD_X509_TOKEN + "</wsse:BinarySecurityToken>\n" + "</wst:OnBehalfOf>\n";
        String body = getSoapEnvelope(onBehalfOf);

        given().log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(500))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .log()
                .all();

    }

    @Test
    public void testSamlAssertionInHeaders() throws Exception {
        String onBehalfOf = "<wst:OnBehalfOf>"
                + "                    <wsse:UsernameToken xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">\n"
                + "                        <wsse:Username>admin</wsse:Username>\n"
                + "                        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">admin</wsse:Password>\n"
                + "                   </wsse:UsernameToken>\n"
                + "                </wst:OnBehalfOf>\n";
        String body = getSoapEnvelope(onBehalfOf);

        String assertionHeader = given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(200))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .extract()
                .response()
                .asString();
        assertionHeader = assertionHeader.substring(assertionHeader.indexOf("<saml2:Assertion"),
                assertionHeader.indexOf("</saml2:Assertion>") + "</saml2:Assertion>".length());

        LOGGER.trace(assertionHeader);

        //try that admin level assertion token on a restricted resource
        given().header(SecurityConstants.SAML_HEADER_NAME,
                "SAML " + RestSecurity.deflateAndBase64Encode(assertionHeader))
                .when()
                .get(ADMIN_PATH.getUrl())
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200));
    }

    @Test
    public void testGoodHokSamlAssertionInHeaders() throws Exception {
        String body = getSoapEnvelope(GOOD_HOK_EXAMPLE, null);

        String assertionHeader = given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(200))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .extract()
                .response()
                .asString();
        assertionHeader = assertionHeader.substring(assertionHeader.indexOf("<saml2:Assertion"),
                assertionHeader.indexOf("</saml2:Assertion>") + "</saml2:Assertion>".length());

        LOGGER.trace(assertionHeader);

        //try that admin level assertion token on a restricted resource
        given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .header(SecurityConstants.SAML_HEADER_NAME,
                        "SAML " + RestSecurity.deflateAndBase64Encode(assertionHeader))
                .when()
                .get(ADMIN_PATH.getUrl())
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200));
    }

    @Test
    public void testBadHokSamlAssertionInHeaders() throws Exception {
        String body = getSoapEnvelope(BAD_HOK_EXAMPLE, null);

        String assertionHeader = given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .log()
                .all()
                .body(body)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue")
                .expect()
                .statusCode(equalTo(200))
                .when()
                .post(SERVICE_ROOT.getUrl() + "/SecurityTokenService")
                .then()
                .extract()
                .response()
                .asString();
        assertionHeader = assertionHeader.substring(assertionHeader.indexOf("<saml2:Assertion"),
                assertionHeader.indexOf("</saml2:Assertion>") + "</saml2:Assertion>".length());

        LOGGER.trace(assertionHeader);

        //try that admin level assertion token on a restricted resource
        given().auth()
                .certificate(KEY_STORE_PATH,
                        PASSWORD,
                        certAuthSettings().sslSocketFactory(SSLSocketFactory.getSystemSocketFactory()))
                .header(SecurityConstants.SAML_HEADER_NAME,
                        "SAML " + RestSecurity.deflateAndBase64Encode(assertionHeader))
                .when()
                .get(ADMIN_PATH.getUrl())
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(401));
    }

    private String getSoapEnvelope(String onBehalfOf) {
        return getSoapEnvelope(SOAP_ENV, onBehalfOf);
    }

    private String getSoapEnvelope(String body, String onBehalfOf) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.500Z'");
        format.setCalendar(calendar);
        String created = format.format(new Date(calendar.getTimeInMillis()));
        long now = calendar.getTimeInMillis();
        now += 60000;
        String expires = format.format(new Date(now));
        if (onBehalfOf != null) {
            body = body.replace("ON_BEHALF_OF", onBehalfOf);
        }
        body = body.replace("CREATED", created);
        body = body.replace("EXPIRES", expires);
        return body;
    }

    String getKeystoreFilename() {
        return System.getProperty("javax.net.ssl.keyStore");
    }

    String getBackupFilename() {
        return getKeystoreFilename() + ".backup";
    }

    void getBackupKeystoreFile() throws IOException {
        Files.copy(Paths.get(getKeystoreFilename()),
                Paths.get(getBackupFilename()),
                REPLACE_EXISTING);
    }

    void restoreKeystoreFile() throws IOException {
        Files.copy(Paths.get(getBackupFilename()),
                Paths.get(getKeystoreFilename()),
                REPLACE_EXISTING);
    }

    //Purpose is to make sure operations of the security certificate generator are accessible
    //at runtime. The actual functionality of these operations is proved in unit tests.
    @Test
    public void testCertificateGeneratorService() throws Exception {
        String commonName = "myCn";
        String expectedValue = "CN=" + commonName;
        String featureName = "security-certificate";
        String certGenPath = SECURE_ROOT_AND_PORT
                + "/admin/jolokia/exec/org.codice.ddf.security.certificate.generator.CertificateGenerator:service=certgenerator";
        getBackupKeystoreFile();
        try {
            getServiceManager().startFeature(true, featureName);

            //Test first operation
            Response response = given().auth()
                    .basic("admin", "admin")
                    .when()
                    .get(certGenPath + "/configureDemoCert/" + commonName);
            String actualValue = JsonPath.from(response.getBody()
                    .asString())
                    .getString("value");
            assertThat(actualValue, equalTo(expectedValue));

            //Test second operation
            response = given().auth()
                    .basic("admin", "admin")
                    .when()
                    .get(certGenPath + "/configureDemoCertWithDefaultHostname");

            String jsonString = response.getBody()
                    .asString();
            JsonPath jsonPath = JsonPath.from(jsonString);
            //If the key value exists, the return value is well-formatted (i.e. not a stacktrace)
            assertThat(jsonPath.getString("value"), notNullValue());

            //Make sure an invalid key would return null
            assertThat(jsonPath.getString("someinvalidkey"), nullValue());

            getServiceManager().stopFeature(false, featureName);
        } finally {
            restoreKeystoreFile();
        }
    }

    @Test
    public void testTransportSoapPolicy() {
        //verify that transport policy is observed indirectly by verifying that a security header with a timestamp was added to the response
        given().log()
                .all()
                .body(SAMPLE_SOAP)
                .header("Content-Type", "application/json")
                .when()
                .post(SERVICE_ROOT + "/sdk/SoapTransportService")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .body(hasXPath("/Envelope/Header/Security/Timestamp"));

    }

    @Test
    public void testAsymmetricSoapPolicy() {
        //verify that asymmetric policy is observed indirectly by verifying that a security header with encrypted data was added to the response
        given().log()
                .all()
                .body(SAMPLE_SOAP)
                .header("Content-Type", "application/json")
                .when()
                .post(SERVICE_ROOT + "/sdk/SoapAsymmetricService")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .body(hasXPath("/Envelope/Header/Security/EncryptedData"));

    }

    @Test
    public void testSymmetricSoapPolicy() {
        //verify that symmetric policy is observed indirectly by verifying that a security header with a signature was added to the response
        given().log()
                .all()
                .body(SAMPLE_SOAP)
                .header("Content-Type", "application/xml")
                .when()
                .post(SERVICE_ROOT + "/sdk/SoapSymmetricService")
                .then()
                .log()
                .all()
                .assertThat()
                .statusCode(equalTo(200))
                .body(hasXPath("/Envelope/Header/Security/Signature/SignatureValue"));

    }

    //ConfigurationAdmin tests
    @Test
    public void testAdminConfigPolicyGetServices() {

        String getAllServicesResponsePermitted = sendPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.ui.admin.api.ConfigurationAdmin:service=ui,version=2.3.0/listServices");

        for (String service : SERVICES_TO_FILTER) {
            assertTrue(getAllServicesResponsePermitted.contains(service));
        }

        String getAllServicesResponseNotPermitted = sendNotPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.ui.admin.api.ConfigurationAdmin:service=ui,version=2.3.0/listServices");

        //Verify there are configurations the user can see other than the restricted ones
        assertTrue(getAllServicesResponseNotPermitted.contains(
                "org.codice.ddf.ui.admin.api.ConfigurationAdmin"));

        for (String filteredService : SERVICES_TO_FILTER) {
            assertFalse(getAllServicesResponseNotPermitted.contains(filteredService));
        }
    }

    @Test
    public void testAdminConfigPolicyCreateAndModifyConfiguration() {

        //create config CreateFactoryConfiguration

        String createFactoryConfigPermittedResponse = sendPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.ui.admin.api.ConfigurationAdmin:service=ui,version=2.3.0/createFactoryConfiguration/testCreateFactoryPid");
        assertThat(JsonPath.given(createFactoryConfigPermittedResponse)
                .getString("value"), containsString("testCreateFactoryPid"));

        String createFactoryConfigNotPermittedResponse = sendNotPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.ui.admin.api.ConfigurationAdmin:service=ui,version=2.3.0/createFactoryConfiguration/testCreateFactoryPid");
        assertNull(JsonPath.given(createFactoryConfigNotPermittedResponse)
                .get("value"));

        //get config GetConfigurations

        String getConfigurationsPermitted = sendPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.ui.admin.api.ConfigurationAdmin:service=ui,version=2.3.0/getConfigurations/(service.pid=ddf.security.pdp.realm.AuthzRealm)");
        assertThat(JsonPath.given(getConfigurationsPermitted)
                .getString("value"), containsString("ddf.security.pdp.realm.AuthzRealm"));

        String getConfigurationsNotPermitted = sendNotPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.ui.admin.api.ConfigurationAdmin:service=ui,version=2.3.0/getConfigurations/(service.pid=ddf.security.pdp.realm.AuthzRealm)");
        assertEquals(JsonPath.given(getConfigurationsNotPermitted)
                .getString("value"), "[]");
    }

    // ApplicationService tests
    @Test
    public void testAdminConfigPolicyGetAllFeaturesAndApps() {

        String getAllFeaturesResponseNotPermitted = sendNotPermittedRequest(
                "/admin/jolokia/read/org.codice.ddf.admin.application.service.ApplicationService:service=application-service");

        String filteredApplications = JsonPath.given(getAllFeaturesResponseNotPermitted)
                .getString("value.Applications.name");

        String filteredFeatures = JsonPath.given(getAllFeaturesResponseNotPermitted)
                .getString("value.AllFeatures.name");

        for (String app : getDefaultRequiredApps()) {
            assertThat(filteredApplications, not(containsString(app)));
        }

        for (String feature : FEATURES_TO_FILTER) {
            assertThat(filteredFeatures, not(containsString(feature)));
        }

        String getAllFeaturesResponsePermitted = sendPermittedRequest(
                "/admin/jolokia/read/org.codice.ddf.admin.application.service.ApplicationService:service=application-service");

        filteredApplications = JsonPath.given(getAllFeaturesResponsePermitted)
                .getString("value.Applications.name");
        filteredFeatures = JsonPath.given(getAllFeaturesResponsePermitted)
                .getString("value.AllFeatures.name");

        for (String app : getDefaultRequiredApps()) {
            assertThat(filteredApplications, containsString(app));
        }

        for (String feature : FEATURES_TO_FILTER) {
            assertThat(filteredFeatures, containsString(feature));
        }
    }

    @Test
    public void testAdminConfigPolicyConfigureApplication() {

        //stop sdk-app
        String stopApplicationNotPermitted = sendNotPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/stopApplication/sdk-app");
        assertFalse(JsonPath.given(stopApplicationNotPermitted)
                .get("value"));

        String stopApplicationPermitted = sendPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/stopApplication/sdk-app");
        assertTrue(JsonPath.given(stopApplicationPermitted)
                .get("value"));

        //remove sdk-app
        sendNotPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/removeApplication/sdk-app");

        String checkApplicationRemovedResponse = sendPermittedRequest(
                "/admin/jolokia/read/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/Applications");
        checkApplicationRemovedResponse = JsonPath.given(checkApplicationRemovedResponse)
                .getString("value.name");
        assertThat(checkApplicationRemovedResponse, containsString("sdk-app"));

        sendPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/removeApplication/sdk-app");

        checkApplicationRemovedResponse = sendPermittedRequest(
                "/admin/jolokia/read/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/Applications");
        checkApplicationRemovedResponse = JsonPath.given(checkApplicationRemovedResponse)
                .getString("value.name");
        assertThat(checkApplicationRemovedResponse, not(containsString("sdk-app")));

        //add the sdk-app back
        given().auth()
                .basic("admin", "admin")
                .contentType(ContentType.JSON)
                .body(ADD_SDK_APP_JOLOKIA_REQ)
                .post(SECURE_ROOT_AND_PORT
                        + "/admin/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/addApplications")
                .body()
                .print();

        //start sdk-app
        String startApplicationNotPermitted = sendNotPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/startApplication/sdk-app");
        assertFalse(JsonPath.given(startApplicationNotPermitted)
                .get("value"));

        String startApplicationPermitted = sendPermittedRequest(
                "/admin/jolokia/exec/org.codice.ddf.admin.application.service.ApplicationService:service=application-service/startApplication/sdk-app");
        assertTrue(JsonPath.given(startApplicationPermitted)
                .get("value"));
    }

    private void waitForSecurityHandlers(String url) throws InterruptedException {
        long stop = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        while (get(url).statusCode() == 503) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() > stop) {
                fail("Failed waiting for security handlers to become available.");
            }
        }
    }

    public String sendPermittedRequest(String jolokiaEndpoint) {
        return given().auth()
                .basic("admin", "admin")
                .get(SECURE_ROOT_AND_PORT + jolokiaEndpoint)
                .body()
                .print();
    }

    public String sendNotPermittedRequest(String jolokiaEndpoint) {
        return given().auth()
                .basic(SYSTEM_ADMIN_USER, SYSTEM_ADMIN_USER_PASSWORD)
                .get(SECURE_ROOT_AND_PORT + jolokiaEndpoint)
                .body()
                .print();
    }
}
