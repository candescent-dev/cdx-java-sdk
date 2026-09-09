package com.candescent.examples.test;

import okhttp3.mockwebserver.MockResponse;
import okio.Buffer;

/**
 * Minimal JSON/XML response stubs for mocked example runs
 * (mirrors examples/typescript/test/fixtures.ts).
 */
final class MockFixtures {
    private MockFixtures() {}

    private static final String MINIMAL_ACCOUNT_JSON =
            """
            {
              "id":"acct-001",
              "institutionUserId":"inst-user-1",
              "institutionId":"05523",
              "accountNumber":"1234",
              "category":"DEPOSIT",
              "type":{"value":"CHECKING"},
              "nickName":"Checking"
            }
            """;

    static MockResponse oauthTokenResponse() {
        return jsonOk(
                """
                {
                  "access_token": "mock-access-token",
                  "expires_in": 3600,
                  "token_type": "Bearer",
                  "refresh_token": "mock-refresh"
                }
                """);
    }

    static MockResponse v1OAuthXmlResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("content-type", "application/xml")
                .setBody(
                        "<token><access_token>mock-v1-token</access_token>"
                                + "<expires_in>3600</expires_in></token>");
    }

    static MockResponse jsonOk(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("content-type", "application/json")
                .setBody(body);
    }

    static MockResponse jsonError(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("content-type", "application/json")
                .setBody(body);
    }

    /** Default API payloads keyed by URL substring. */
    static MockResponse defaultFixtureForUrl(String url, String method) {
        String s = url.toLowerCase();
        String m = method.toUpperCase();

        if (s.contains("/oauth2/v1/token") || s.contains("/v1/oauth/token")) {
            return s.contains("/v1/oauth/token") ? v1OAuthXmlResponse() : oauthTokenResponse();
        }
        if (s.contains("/revoke")) {
            return new MockResponse().setResponseCode(204);
        }

        if ((s.contains("/v1/accounts") || s.contains("/db-accounts/")) && "GET".equals(m)) {
            if (s.contains("00000000-0000-0000-0000-000000000000") || s.contains("not-a-valid")) {
                return jsonError(404, "{\"message\":\"not found\"}");
            }
            if (s.matches("(?s).*/accounts/[^/?]+.*")) {
                return jsonOk(MINIMAL_ACCOUNT_JSON);
            }
            if (!s.contains("hostuserid=") && !s.contains("loginid=")) {
                return jsonError(400, "{\"message\":\"hostUserId or loginId required\"}");
            }
            return jsonOk("{\"accounts\":[" + MINIMAL_ACCOUNT_JSON + "]}");
        }

        if (s.contains("/transactions")) {
            return jsonOk("{\"transactions\":[]}");
        }
        if (s.contains("/recipients")) {
            return jsonOk("{\"recipients\":[{\"id\":\"rcp-001\",\"nickName\":\"Test\"}]}");
        }
        if (s.contains("/transfers")) {
            return jsonOk("{\"transferId\":\"xfer-001\"}");
        }
        if (s.contains("/fis/") && s.contains("ficustomers") && "POST".equals(m)) {
            return new MockResponse()
                    .setResponseCode(200)
                    .setHeader("content-type", "application/xml")
                    .setBody(
                            "<?xml version=\"1.0\"?><RegisterCustomerResponse><FICustomer>"
                                    + "<id><value>cust-guid-001</value></id>"
                                    + "<memberNumber>123456</memberNumber></FICustomer>"
                                    + "</RegisterCustomerResponse>");
        }
        if (s.contains("/ux-users/") || s.contains("/profile")) {
            return jsonOk("{\"userId\":\"inst-user-1\",\"status\":\"ACTIVE\"}");
        }
        if (s.contains("/entitlements")) {
            return jsonOk("{\"entitlements\":[],\"userEntitlements\":[],\"businessEntitlements\":[]}");
        }
        if (s.contains("/ach-payments") || s.contains("/wire-payments")) {
            return jsonOk("POST".equals(m) ? "{\"paymentId\":\"pay-001\"}" : "{\"payments\":[]}");
        }
        if (s.contains("/subscriptions") || s.contains("/notification")) {
            return jsonOk("{\"subscriptions\":[]}");
        }
        if (s.contains("/alert") || s.contains("/templates") || s.contains("/history")) {
            return jsonOk("[]");
        }
        if (s.contains("/disclosure")) {
            return jsonOk("{\"disclosures\":[]}");
        }
        if (s.contains("/e-statement") || s.contains("/estatement")) {
            return jsonOk("{\"reports\":[]}");
        }
        if (s.contains("/groups") || s.contains("/audience") || s.contains("/promotions")) {
            return jsonOk("{\"items\":[]}");
        }
        if (s.contains("/jobs")) {
            return jsonOk("{\"jobs\":[]}");
        }
        if (s.contains("/mx")) {
            if (s.contains("/download/") || s.contains("/snapshot/")) {
                Buffer body = new Buffer().write(new byte[] {1, 2, 3});
                return new MockResponse()
                        .setResponseCode(200)
                        .setHeader("content-type", "application/vnd.mx.logs.v1+avro")
                        .setBody(body);
            }
            return jsonOk(
                    "{\"users\":[{\"id\":\"mx-user-1\",\"guid\":\"mx-user-1\"}],"
                            + "\"url\":\"https://example.com/widget\"}");
        }
        if (s.contains("/auth-code") || s.contains("/oauth")) {
            return jsonOk("{\"authorizationCode\":\"auth-code-1\",\"token\":\"tok\"}");
        }
        if (s.contains("/bankingservices/")) {
            return jsonOk("{\"accounts\":[]}");
        }

        return jsonOk("{}");
    }
}
