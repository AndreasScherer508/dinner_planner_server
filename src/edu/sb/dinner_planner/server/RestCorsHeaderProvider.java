package edu.sb.dinner_planner.server;

import jakarta.annotation.Priority;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import edu.sb.tool.Copyright;


/**
 * JAX-RS filter provider that adds the headers required for cross-origin resource
 * sharing (<i>CORS</i>) to any HTTP response created by a REST service method
 * answering an HTTP request that contains an "Origin" header entry.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
@Copyright(year=2013, holders="Sascha Baumeister")
public class RestCorsHeaderProvider implements ContainerResponseFilter {
	static private final String REQUEST_HEADERS = "Access-Control-Request-Headers";
	static private final String REQUEST_METHOD = "Access-Control-Request-Method";
	static private final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
	static private final String ALLOW_METHODS = "Access-Control-Allow-Methods";
	static private final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
	static private final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
	static private final String[] ALLOWED_METHODS = { HttpMethod.OPTIONS, HttpMethod.HEAD, HttpMethod.GET, HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.POST, HttpMethod.PUT };


	/**
	 * Adds the CORS headers to any filtered HTTP response.
	 * @param request the HTTP request context
	 * @param response the HTTP response context
	 * @throws NullPointerException if the given response context is null
	 */
	public void filter (final ContainerRequestContext request, final ContainerResponseContext response) throws NullPointerException {
		final MultivaluedMap<String,String> requestHeaders = request.getHeaders();
		final MultivaluedMap<String,Object> responseHeaders = response.getHeaders();
		final String origin = requestHeaders.getFirst("Origin");
		if (origin == null) return;

		responseHeaders.putSingle(ALLOW_ORIGIN, origin.equals("null") ? "*" : origin);
		if (!origin.equals("null")) {
			final String responseVary = responseHeaders.containsKey(HttpHeaders.VARY) ? String.join(", ", responseHeaders.get(HttpHeaders.VARY).toArray(String[]::new)) : null;
			responseHeaders.putSingle(HttpHeaders.VARY, responseVary == null ? "Origin" : responseVary + ", Origin");
			responseHeaders.putSingle(ALLOW_CREDENTIALS, true);
		}

		final String requestedHeaders = requestHeaders.containsKey(REQUEST_HEADERS) ? String.join(", ", requestHeaders.get(REQUEST_HEADERS).toArray(String[]::new)) : null;
		if (requestedHeaders != null) responseHeaders.putSingle(ALLOW_HEADERS, requestedHeaders);

		final String requestedMethod = requestHeaders.containsKey(REQUEST_METHOD) ? requestHeaders.get(REQUEST_METHOD).get(0) : null;
		if (requestedMethod != null) responseHeaders.putSingle(ALLOW_METHODS, String.join(", ", ALLOWED_METHODS));
	}
}