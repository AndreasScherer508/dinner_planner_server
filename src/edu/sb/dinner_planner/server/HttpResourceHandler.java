package edu.sb.dinner_planner.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import edu.sb.tool.Copyright;
import edu.sb.tool.ZipPaths;


/**
 * HTTP handler class providing HTTP access to basic web resources contained within
 * file systems or code repositories for the JDK HTTP server.
 */
@Copyright(year=2010, holders="Sascha Baumeister")
public class HttpResourceHandler implements HttpHandler {
	static public enum Method { GET, HEAD, POST, PUT, PATCH, DELETE, CONNECT, TRACE, OPTIONS }
	static private final short OK = 200, NO_CONTENT = 204, NOT_FOUND = 404, METHOD_NOT_ALLOWED = 405;
	static private final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
	static private Map<String,String> CONTENT_TYPES = Stream.of(new String[][] {
		{ "bin", DEFAULT_CONTENT_TYPE },
		{ "xhtml", "application/xhtml+xml" },
		{ "html", "text/html" }, { "htm", "text/html" }, { "css", "text/css" },
		{ "js", "application/javascript" },
		{ "java", "application/java" }, { "class", "application/java" }, { "jar", "application/java" }, 
		{ "txt", "text/plain" }, { "tpl", "text/plain" },
		{ "rtf", "application/rtf" }, { "pdf", "application/pdf" },
		{ "ps", "application/postscript" },	{ "eps", "application/postscript" },
		{ "jpeg", "image/jpeg" }, { "jpg", "image/jpeg" }, { "gif", "image/gif" }, { "png", "image/png" }, { "svg", "image/svg+xml" },
		{ "wav", "audio/wav" }, { "mp3", "audio/mp3" }, { "m4a", "audio/mp4" }, { "m4b", "audio/mp4" }, { "ogg", "audio/ogg" },
		{ "mpeg", "video/mpeg" }, { "mpg", "video/mpeg" }, { "mp4", "video/mp4" }, { "webm", "video/webm" }, { "flv", "video/x-flv" }, { "mov", "video/quicktime" }
	}).collect(Collectors.toMap(array -> array[0], array -> array[1]));

	private final String contextPath;
	private final Path resourceDirectory;
	private final Set<Method> methods = new CopyOnWriteArraySet<>(Arrays.asList(Method.GET, Method.OPTIONS));
	private final Map<String,String> contentTypes = new ConcurrentHashMap<>(CONTENT_TYPES);


	/**
	 * Configures a new instance for class loader based resource access.
	 * @param contextPath the context path
	 * @throws NullPointerException if the given argument is null
	 */
	public HttpResourceHandler (final String contextPath) throws NullPointerException {
		this(contextPath, null);
	}


	/**
	 * Configures a new instance for file system based resource access.
	 * @param contextPath the context path
	 * @param resourceDirectory the external resource directory
	 * @throws NullPointerException if any of the given arguments is null
	 */
	public HttpResourceHandler (final String contextPath, final Path resourceDirectory) throws NullPointerException {
		if (resourceDirectory == null) throw new NullPointerException();

		this.contextPath = normalizeContextPath(contextPath);
		this.resourceDirectory = ZipPaths.isZipPath(resourceDirectory)
			? resourceDirectory
			: resourceDirectory.toAbsolutePath();
	}


	/**
	 * Returns the context path without it's terminal slash (except for root paths).
	 * @return the context path
	 */
	public String getContextPath () {
		return this.contextPath.length() == 1 ? this.contextPath : this.contextPath.substring(0, this.contextPath.length() - 1);
	}


	/**
	 * Returns the resource directory
	 * @return the resource directory, or {@code null} for none
	 */
	public Path getResourceDirectory () {
		return this.resourceDirectory;
	}


	/**
	 * Returns the methods.
	 * @return the supported methods
	 */
	public Set<Method> getMethods () {
		return this.methods;
	}


	/**
	 * Returns the handler's content type mappings. Note that the resulting map allows the registration of additional
	 * content types.
	 * @return the content type mappings
	 */
	public Map<String,String> getContentTypes () {
		return this.contentTypes;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {
		return String.format("%s(contextPath=%s, contextDirectory=%s, methods=%s)", this.getClass().getName(), this.getContextPath(), this.resourceDirectory, this.methods);
	}


	/**
	 * Returns the resource path.
	 * @param requestPath the request path
	 * @return the resource path, relative to this handler's context path
	 * @throws NullPointerException if the given argument is null
	 * @throws IllegalArgumentException if the given argument does not start with the context path
	 */
	public final String resourcePath (final String requestPath) throws NullPointerException, IllegalArgumentException {
		if (!requestPath.startsWith(this.contextPath)) throw new IllegalArgumentException();

		return requestPath.substring(this.contextPath.length());
	}


	/**
	 * Returns the resource size.
	 * @param resourcePath the resource path, relative to this handler's context directory
	 * @return the resource size, or {@code null} for none
	 * @throws NullPointerException if the given argument is null
	 * @throws IllegalArgumentException if the given argument does not start with the context path
	 */
	public final Long resourceSize (final String resourcePath) throws NullPointerException, IllegalArgumentException {
		if (resourcePath == null) throw new NullPointerException();

		final Path resolvedPath = this.resourceDirectory.resolve(resourcePath);
		try {
			return ZipPaths.isZipPath(this.resourceDirectory)
				? this.resourceStream(resolvedPath.toString()).readAllBytes().length
				: Files.size(resolvedPath);
		} catch (final IOException exception) {
			return null;
		}
	}


	/**
	 * Returns the resource type.
	 * @param resourcePath the resource path, relative to this handler's context directory
	 * @return the resource type
	 * @throws NullPointerException if the given argument is null
	 */
	public final String resourceType (final String resourcePath) throws NullPointerException {
		if (resourcePath == null) throw new NullPointerException();

		final String resourceExtension = resourcePath.substring(resourcePath.lastIndexOf('.') + 1).toLowerCase();
		return this.contentTypes.getOrDefault(resourceExtension, DEFAULT_CONTENT_TYPE);
	}


	/**
	 * Returns a new resource stream.
	 * @param resourcePath the resource path, relative to this handler's context directory
	 * @return the resource stream created
	 * @throws NullPointerException if the given argument is null
	 * @throws IOException if there is an I/O related problem
	 */
	public final InputStream resourceStream (String resourcePath) throws NullPointerException, IOException {
		final Path resolvedResourcePath = this.resourceDirectory.resolve(resourcePath);
		final InputStream byteSource = ZipPaths.isZipPath(this.resourceDirectory)
			? Thread.currentThread().getContextClassLoader().getResourceAsStream(resolvedResourcePath.toString())
			: Files.newInputStream(resolvedResourcePath);
		if (byteSource == null) throw new NoSuchFileException(resourcePath);

		return byteSource;
	}


	/**
	 * Handles the given HTTP exchange by copying the content of it's request path to it's response. The request path is
	 * interpreted to be relative to the handler's context directory, all path's outside of this scope are inaccessible.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	public void handle (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		try {
			if (!exchange.getRequestURI().getPath().startsWith(this.contextPath)) {
				exchange.sendResponseHeaders(NOT_FOUND, -1);
				return;
			}

			final Method method;
			try {
				method = Method.valueOf(exchange.getRequestMethod().trim().toUpperCase());
			} catch (final IllegalArgumentException exception) {
				exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
				return;
			}

			switch (method) {
				case GET:		this.handleGetRequest(exchange); break;
				case HEAD:		this.handleHeadRequest(exchange); break;
				case POST:		this.handlePostRequest(exchange); break;
				case PUT:		this.handlePutRequest(exchange); break;
				case PATCH:		this.handlePatchRequest(exchange); break;
				case DELETE:	this.handleDeleteRequest(exchange); break;
				case CONNECT:	this.handleConnectRequest(exchange); break;
				case TRACE:		this.handleTraceRequest(exchange); break;
				case OPTIONS:	this.handleOptionsRequest(exchange); break;
				default:		throw new AssertionError();
			}
		} finally {
			exchange.close();
		}
	}


	/**
	 * Handles an HTTP GET request by returning the resource content.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handleGetRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		final String resourcePath = this.resourcePath(exchange.getRequestURI().getPath());
		exchange.getResponseHeaders().add("Content-Type", this.resourceType(resourcePath));

		try (InputStream resourceStream = this.resourceStream(resourcePath)) {
			try (OutputStream bodyStream = exchange.getResponseBody()) {
				final Long resourceSize = this.resourceSize(resourcePath);
				final int responseCode = resourceSize == null || resourceSize != 0 ? OK : NO_CONTENT;
				exchange.sendResponseHeaders(responseCode, resourceSize == null ? 0 : resourceSize);
				resourceStream.transferTo(bodyStream);
			}
		} catch (final NoSuchFileException | AccessDeniedException exception) {
			exchange.sendResponseHeaders(NOT_FOUND, -1);
		}
	}


	/**
	 * Handles an HTTP HEAD request.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handleHeadRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
	}


	/**
	 * Handles an HTTP POST request.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handlePostRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
	}

	
	/**
	 * Handles an HTTP PUT request.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handlePutRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
	}

	
	/**
	 * Handles an HTTP PATCH request.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handlePatchRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
	}


	/**
	 * Handles an HTTP DELETE request.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handleDeleteRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
	}


	/**
	 * Handles an HTTP CONNECT request.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handleConnectRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
	}


	/**
	 * Handles an HTTP TRACE request.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handleTraceRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1);
	}


	/**
	 * Handles an HTTP OPTIONS request.
	 * @param exchange the HTTP exchange
	 * @throws NullPointerException if the given exchange is null
	 * @throws IllegalArgumentException if the given exchange contains syntactically invalid data
	 * @throws IllegalStateException if there is constraint violation
	 * @throws IOException if there is an I/O related problem
	 */
	protected void handleOptionsRequest (final HttpExchange exchange) throws NullPointerException, IllegalArgumentException, IllegalStateException, IOException {
		exchange.getResponseHeaders().add("Content-Type", "text/plain");

		try (OutputStream bodyStream = exchange.getResponseBody()) {
			final byte[] document = this.methods.stream().map(Method::name).sorted().collect(Collectors.joining("\n")).getBytes(UTF_8);
			exchange.sendResponseHeaders(OK, document.length);

			bodyStream.write(document);
		}
	}


	/**
	 * Returns the normalized context path.
	 * @param contextPath the context path
	 * @return the normalized context path with leading and trailing slashes
	 * @throws NullPointerException if the given argument is null
	 */
	static private String normalizeContextPath (String contextPath) throws NullPointerException {
		if (!contextPath.startsWith("/")) contextPath = "/" + contextPath;
		if (!contextPath.endsWith("/")) contextPath += "/";
		return contextPath;
	}
}