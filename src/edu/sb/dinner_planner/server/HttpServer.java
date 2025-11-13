package edu.sb.dinner_planner.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import javax.net.ssl.SSLContext;
import edu.sb.tool.Copyright;
import edu.sb.tool.ZipPaths;



/**
 * HTTP server application facade capable of serving both external and internal file content.
 */
@Copyright(year=2014, holders="Sascha Baumeister")
public final class HttpServer {
	static private final int DEFAULT_PORT = 8001;
	static private final String DEFAULT_RESOURCE_DIRECTORY = "WEB-INF";
	static private final String DEFAULT_KEY_STORE_PASSWORD = "changeit";
	static private final Path APPLICATION_DIRECTORY = Files.isDirectory(Paths.get(".")) ? Paths.get(".").toAbsolutePath().normalize() : null;


	/**
	 * Prevents external instantiation.
	 */
	private HttpServer () {}


	/**
	 * Application entry point. The given arguments are expected to be an optional service socket address
	 * consisting of either a hostname:port combination or solely the port (default is 8001), an optional
	 * external resource directory path (default is the VM temporary directory), an optional key-store
	 * file path for HTTPS (default is {@code null} for HTTP), a key store password (default is"changeit"),
	 * and an optional key store device password (default is {@code null}).
	 * @param args the runtime arguments
	 * @throws IllegalArgumentException if the given port is not a valid port number
	 * @throws NotDirectoryException if the given external resource directory path is not a directory
	 * @throws NoSuchFileException if the given key store file path is neither {@code null} nor representing a regular file
	 * @throws IOException if there is an I/O related problem
	 * @throws GeneralSecurityException if none of the installed providers supports the specified key store file type,
	 * 			if any of the given passwords is invalid, if any of the certificates within the key store could not be
	 *  		loaded, or if the key store has expired
	 */
	static public void main (final String[] args) throws IllegalArgumentException, NotDirectoryException, NoSuchFileException, IOException, GeneralSecurityException {
		final String socketAddress = args.length > 0 && !args[0].isBlank() ? args[0].trim() : Integer.toString(DEFAULT_PORT);
		final String serviceHostname = socketAddress.contains(":") ? socketAddress.substring(0, socketAddress.indexOf(':')) : TcpServers.localAddress().getCanonicalHostName();
		final int servicePort = Integer.parseInt(socketAddress.contains(":") ? socketAddress.substring(socketAddress.indexOf(':') + 1) : socketAddress);
		final Path internalResourceDirectory = Thread.currentThread().getContextClassLoader().getResourceAsStream(DEFAULT_RESOURCE_DIRECTORY) == null ? null : ZipPaths.get(DEFAULT_RESOURCE_DIRECTORY);
		final Path externalResourceDirectory = args.length > 1 && !args[1].isBlank() ? Paths.get(args[1].trim()).toAbsolutePath() : (internalResourceDirectory == null ? APPLICATION_DIRECTORY : null);
		final Path keyStorePath = args.length > 2 && !args[2].isBlank() ? Paths.get(args[2].trim()).toAbsolutePath() : null;
		final String keyStorePassword = args.length > 3 && !args[3].isBlank() ? args[3].trim() : DEFAULT_KEY_STORE_PASSWORD;
		final String keyStoreDevicePassword = args.length > 4 && !args[4].isBlank() ? args[4].trim() : null;

		// if (!TcpServers.localHostnames().anyMatch(hostname -> hostname.startsWith(serviceHostname))) throw new IllegalArgumentException("configured service host name is illegal: " + serviceHostname);
		if (servicePort < 1 || servicePort > 65535) throw new IllegalArgumentException("configured service port is illegal: " + servicePort);
		if (internalResourceDirectory == null & externalResourceDirectory == null) throw new IllegalArgumentException("no resource directory available!");
		if (externalResourceDirectory != null && !Files.isDirectory(externalResourceDirectory)) throw new NotDirectoryException(externalResourceDirectory.toString());

		// Create HTTP/HTTPS server configuration
		final String serviceProtocol = keyStorePath == null ? "http" : "https";
		final URI serviceURI = URI.create(serviceProtocol + "://" + serviceHostname + ":" + servicePort);
		if (serviceURI.getHost() == null) throw new IllegalStateException("service host name contains illegal characters: " + serviceHostname);
		final InetSocketAddress serviceAddress = new InetSocketAddress(serviceURI.getHost(), serviceURI.getPort());
		final SSLContext tlsContext = keyStorePath == null ? null : TcpServers.newTLSContext(keyStorePath, keyStorePassword, keyStoreDevicePassword);
		if (tlsContext != null) tlsContext.createSSLEngine(serviceURI.getHost(), serviceURI.getPort());

		// Create and start HTTP/HTTPS server
		final com.sun.net.httpserver.HttpServer httpServer = TcpServers.newHttpServer(serviceAddress, tlsContext);
		final HttpResourceHandler internalFileHandler = internalResourceDirectory == null ? null : new HttpResourceHandler(externalResourceDirectory == null ? "/" : "/internal", internalResourceDirectory);
		final HttpResourceHandler externalFileHandler = externalResourceDirectory == null ? null : new HttpResourceHandler(internalResourceDirectory == null ? "/" : "/external", externalResourceDirectory);
		if (internalFileHandler != null)
			httpServer.createContext(internalFileHandler.getContextPath(), internalFileHandler);
		if (externalFileHandler != null)
			httpServer.createContext(externalFileHandler.getContextPath(), externalFileHandler);
		httpServer.start();

		try {
			System.out.format("Web server running on origin \"%s\".%n", serviceURI);
			if (internalFileHandler != null)
				System.out.format("Context path \"%s\" is configured for class loader access within \"%s\".%n", internalFileHandler.getContextPath(), internalFileHandler.getResourceDirectory());
			if (externalFileHandler != null)
				System.out.format("Context path \"%s\" is configured for file system access within \"%s\".%n", externalFileHandler.getContextPath(), externalFileHandler.getResourceDirectory());
			System.out.println("Enter \"quit\" to terminate.");

			final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
			while (!"quit".equals(charSource.readLine()));
		} finally {
			httpServer.stop(0);
		}
	}
}