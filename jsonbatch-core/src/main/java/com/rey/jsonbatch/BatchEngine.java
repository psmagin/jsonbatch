package com.rey.jsonbatch;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.rey.jsonbatch.function.MathUtils;
import com.rey.jsonbatch.model.BatchTemplate;
import com.rey.jsonbatch.model.DispatchOptions;
import com.rey.jsonbatch.model.Request;
import com.rey.jsonbatch.model.RequestTemplate;
import com.rey.jsonbatch.model.Response;
import com.rey.jsonbatch.model.ResponseTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class BatchEngine {

    private Logger logger = LoggerFactory.getLogger(BatchEngine.class);

    private Configuration configuration;
    private JsonBuilder jsonBuilder;
    private RequestDispatcher requestDispatcher;

    private static final String KEY_ORIGINAL = "original";
    private static final String KEY_REQUESTS = "requests";
    private static final String KEY_RESPONSES = "responses";


    public BatchEngine(Configuration configuration,
                       JsonBuilder jsonBuilder,
                       RequestDispatcher requestDispatcher) {
        this.configuration = configuration;
        this.jsonBuilder = jsonBuilder;
        this.requestDispatcher = requestDispatcher;
    }

    public Response execute(Request originalRequest, BatchTemplate template) throws Exception {
        logger.info("Start executing batch with [{}] original request", originalRequest);
        Map<String, Object> batchResponse = new LinkedHashMap<>();
        batchResponse.put(KEY_ORIGINAL, originalRequest.toMap());
        batchResponse.put(KEY_REQUESTS, new ArrayList<>());
        batchResponse.put(KEY_RESPONSES, new ArrayList<>());
        DocumentContext context = JsonPath.using(configuration).parse(configuration.jsonProvider().toJson(batchResponse));
        if(template.getDispatchOptions() == null)
            template.setDispatchOptions(new DispatchOptions());

        RequestTemplate requestTemplate = chooseRequestTemplate(template.getRequests(), context);
        int count = 0;
        while(requestTemplate != null) {
            logger.info("Preparing request with [{}] index", count);
            Request request = buildRequest(requestTemplate, context);
            logger.info("Dispatching request with [{}] index", count);
            Response response = requestDispatcher.dispatch(request, configuration.jsonProvider(), template.getDispatchOptions());
            logger.info("Received response with [{}] status", response.getStatus());
            ((List)batchResponse.get(KEY_REQUESTS)).add(request.toMap());
            ((List)batchResponse.get(KEY_RESPONSES)).add(response.toMap());
            context = JsonPath.using(configuration).parse(configuration.jsonProvider().toJson(batchResponse));
            logger.info("Done executing request with [{}] index", count);

            ResponseTemplate responseTemplate = chooseResponseTemplate(requestTemplate.getResponses(), context);
            if(responseTemplate != null) {
                logger.info("Found break response");
                response = buildResponse(responseTemplate, context);
                logger.info("Done executing batch with [{}] original request", originalRequest);
                return response;
            }

            requestTemplate = chooseRequestTemplate(requestTemplate.getRequests(), context);
            count ++;
        }

        Response response;
        ResponseTemplate responseTemplate = chooseResponseTemplate(template.getResponses(), context);
        if(responseTemplate != null) {
            logger.info("Found final response");
            response = buildResponse(responseTemplate, context);
        }
        else {
            logger.info("Not found final response. Return all batch responses");
            response = new Response();
            response.setStatus(200);
            response.setBody(batchResponse);
        }

        logger.info("Done executing batch with [{}] original request", originalRequest);
        return response;
    }

    private RequestTemplate chooseRequestTemplate(List<RequestTemplate> requestTemplates, DocumentContext context) {
        if(requestTemplates == null)
            return null;
        for(RequestTemplate requestTemplate : requestTemplates) {
            if(requestTemplate.getPredicate() == null || MathUtils.toBoolean(jsonBuilder.build(requestTemplate.getPredicate(), context)))
                return requestTemplate;
        }
        return null;
    }

    private ResponseTemplate chooseResponseTemplate(List<ResponseTemplate> responseTemplates, DocumentContext context) {
        if(responseTemplates == null)
            return null;
        for(ResponseTemplate responseTemplate : responseTemplates) {
            if(responseTemplate.getPredicate() == null || MathUtils.toBoolean(jsonBuilder.build(responseTemplate.getPredicate(), context)))
                return responseTemplate;
        }
        return null;
    }

    private Request buildRequest(RequestTemplate template, DocumentContext context) {
        Request request = new Request();
        request.setHttpMethod(jsonBuilder.build(template.getHttpMethod(), context).toString());
        request.setUrl(jsonBuilder.build(template.getUrl(), context).toString());
        if(template.getBody() != null) {
            request.setBody(jsonBuilder.build(template.getBody(), context));
        }
        if(template.getHeaders() != null) {
            request.setHeaders(buildHeaders((Map<String, Object>)jsonBuilder.build(template.getHeaders(), context)));
        }
        else {
            request.setHeaders(new HashMap<>());
        }
        return request;
    }

    private Response buildResponse(ResponseTemplate template, DocumentContext context) {
        Response response = new Response();
        if(template.getStatus() != null)
            response.setStatus(MathUtils.toInteger(jsonBuilder.build(template.getStatus(), context)));
        else
            response.setStatus(200);
        if(template.getBody() != null)
            response.setBody(jsonBuilder.build(template.getBody(), context));
        if(template.getHeaders() != null)
            response.setHeaders(buildHeaders((Map<String, Object>)jsonBuilder.build(template.getHeaders(), context)));
        return response;
    }

    private Map<String, List<String>> buildHeaders(Map<String, Object> values) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        values.forEach( (key, value) -> {
            if(value instanceof Collection) {
                headers.put(key,  (List<String>)((Collection) value).stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList()));
            }
            else
                headers.put(key, Collections.singletonList(String.valueOf(value)));
        });
        return headers;
    }

}
