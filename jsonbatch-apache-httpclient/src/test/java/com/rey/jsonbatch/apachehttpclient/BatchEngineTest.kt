package com.rey.jsonbatch.apachehttpclient

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import com.rey.jsonbatch.BatchEngine
import com.rey.jsonbatch.JsonBuilder
import com.rey.jsonbatch.function.AverageFunction
import com.rey.jsonbatch.function.MaxFunction
import com.rey.jsonbatch.function.MinFunction
import com.rey.jsonbatch.function.SumFunction
import com.rey.jsonbatch.model.BatchTemplate
import com.rey.jsonbatch.model.Request
import org.apache.http.impl.client.HttpClients
import org.junit.Before
import org.junit.Test

class BatchEngineTest {

    private lateinit var batchEngine: BatchEngine

    private lateinit var objectMapper: ObjectMapper

    @Before
    fun setUp() {
        objectMapper = ObjectMapper()
        objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        val conf = Configuration.builder()
                .jsonProvider(JacksonJsonProvider(objectMapper))
                .mappingProvider(JacksonMappingProvider(objectMapper))
                .build()
        val jsonBuilder = JsonBuilder(SumFunction.instance(),
                AverageFunction.instance(),
                MinFunction.instance(),
                MaxFunction.instance())
        batchEngine = BatchEngine(conf, jsonBuilder, ApacheHttpClientRequestDispatcher(HttpClients.createDefault()))
    }
    
    @Test
    fun test() {
        val template = """
            {
                "requests": [
                    {
                        "http_method": "GET",
                        "url": "https://jsonplaceholder.typicode.com/posts",
                        "headers": {
                            "Accept": "str application/json, */*"
                        },
                        "body": null
                    },
                    {
                        "http_method": "GET",
                        "url": "https://jsonplaceholder.typicode.com/posts/@{int $.responses[0].body[0].id}@",
                        "headers": {
                            "Accept": "str application/json, */*"
                        },
                        "body": null
                    },
                    {
                        "http_method": "POST",
                        "url": "https://jsonplaceholder.typicode.com/posts",
                        "headers": {
                            "Content-type": "str application/json; charset=UTF-8"
                        },
                        "body": {
                            "title": "str A new post",
                            "userId": "int $.responses[1].body.userId",
                            "body": "str $.responses[1].body.body"
                        }
                    }
                ],
                "response": {
                    "headers": null,
                    "body": {
                        "first_post": "obj $.responses[1].body",
                        "new_post": "obj $.responses[2].body"
                    }
                }
            }
        """.trimIndent()
        val original_request = """
            {
                "headers": null,
                "body": null
            }
        """.trimIndent()
        val batchTemplate = objectMapper.readValue(template, BatchTemplate::class.java)
        val originalRequest = objectMapper.readValue(original_request, Request::class.java)

        val finalResponse = batchEngine.execute(originalRequest, batchTemplate)
        println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalResponse))
    }
    
}