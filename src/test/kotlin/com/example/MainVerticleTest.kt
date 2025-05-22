package com.example

import io.vertx.core.Vertx
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.predicate.ResponsePredicate
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class MainVerticleTest {

  @BeforeEach
  fun deployVerticle(vertx: Vertx, testContext: VertxTestContext) {
    vertx.deployVerticle(MainVerticle(), testContext.succeeding<String> { _ -> testContext.completeNow() })
  }

  @Test
  fun `healthz endpoint should return 200 and OK`(vertx: Vertx, testContext: VertxTestContext) {
    val client = WebClient.create(vertx)
    client.get(8888, "localhost", "/healthz")
      .expect(ResponsePredicate.SC_OK) // Checks for 200 status code
      .expect(ResponsePredicate.contentType("text/plain"))
      .send { ar ->
        if (ar.succeeded()) {
          val response = ar.result()
          if (response.bodyAsString() == "OK") {
            testContext.completeNow()
          } else {
            testContext.failNow("Response body was not 'OK': " + response.bodyAsString())
          }
        } else {
          testContext.failNow(ar.cause())
        }
      }
  }
}
