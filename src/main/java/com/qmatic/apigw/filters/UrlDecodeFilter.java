package com.qmatic.apigw.filters;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.qmatic.apigw.properties.UrlDecodeFilterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.zuul.filters.ProxyRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.ProxyRouteLocator.ProxyRouteSpec;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.stereotype.Component;
import sun.misc.BASE64Decoder;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@EnableConfigurationProperties(UrlDecodeFilterProperties.class)
@Component
public class UrlDecodeFilter extends ZuulFilter {

    private static final Logger log = LoggerFactory.getLogger(UrlDecodeFilter.class);

    private static final String QUERY_PARAM_NAME = "e";

    @Autowired
    private RouteLocator routeLocator;
    @Autowired
    protected UrlDecodeFilterProperties urlDecodeFilterProperties;

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 9;
    }

    @Override
    public boolean shouldFilter() {
        return urlDecodeFilterProperties.isEnabled();
    }

    @Override
    public Object run() {
        log.debug("Running UrlDecodeFilter");
        RequestContext ctx = RequestContext.getCurrentContext();
        Map<String, List<String>> requestQueryParams = ctx.getRequestQueryParams();

        if (requestQueryParams == null) {
            return null;
        }
        if (!requestQueryParams.containsKey(QUERY_PARAM_NAME)) {
            return null;
        }

        String encryptedUrl = requestQueryParams.get(QUERY_PARAM_NAME).get(0);
        String decryptedUrl = decrypting(encryptedUrl);
        if(decryptedUrl == null){
            return null;
        }

        ProxyRouteSpec matchingRoute = ((ProxyRouteLocator) routeLocator).getMatchingRoute(decryptedUrl);
        if (matchingRoute == null) {
            return null;
        }

        setNewRequestURI(ctx, matchingRoute);
        setNewProxy(ctx, matchingRoute);
        setRequestParameters(ctx, decryptedUrl);
        setNewRouteHost(ctx, matchingRoute);

        return null;
    }


    private void setNewRequestURI(RequestContext ctx, ProxyRouteSpec matchingRoute) {
        String path = matchingRoute.getPath();
        String newRequestURI = path.substring(1, path.indexOf("?"));
        ctx.put("requestURI", newRequestURI);
    }

    private void setNewProxy(RequestContext ctx, ProxyRouteSpec matchingRoute) {
        String id = matchingRoute.getId();
        ctx.put("proxy", id);
    }

    private void setRequestParameters(RequestContext ctx, String encodedURL) {
        Map<String, List<String>> requestQueryParams = ctx.getRequestQueryParams();
        if (requestQueryParams == null) {
            return;
        }
        requestQueryParams.remove(QUERY_PARAM_NAME);
        String queryParamsStr = encodedURL.substring(encodedURL.indexOf("?") + 1);
        String[] parameters = queryParamsStr.split("&");
        for (String parameter : parameters) {
            String[] nameValue = parameter.split("=");
            requestQueryParams.put(nameValue[0], Arrays.asList(nameValue[1]));
        }
        ctx.setRequestQueryParams(requestQueryParams);
    }

    private void setNewRouteHost(RequestContext ctx, ProxyRouteSpec matchingRoute) {
        try {
            ctx.setRouteHost(new URL(matchingRoute.getLocation()));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private String decrypting(String encrypted64) {
        try {
            SecretKeySpec secretKeySpec = urlDecodeFilterProperties.getSecretKeySpec();

            // BASE64 String to Byte-Array
            BASE64Decoder myDecoder2 = new BASE64Decoder();
            byte[] crypted2 = myDecoder2.decodeBuffer(encrypted64);

            // Decrypt
            Cipher cipher2 = Cipher.getInstance("AES");
            cipher2.init(Cipher.DECRYPT_MODE, secretKeySpec);
            byte[] cipherData2 = cipher2.doFinal(crypted2);
            String url = new String(cipherData2);
            return url;
        } catch (Exception e) {
            log.error("Failed to decrypt value in request. Message:" + e.getMessage(), e);
        }
        return null;
    }
}