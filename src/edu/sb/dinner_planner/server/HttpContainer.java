package edu.sb.dinner_planner.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import com.sun.net.httpserver.HttpServer;
import edu.sb.tool.Copyright;
import edu.sb.tool.ZipPaths;


/**
 * <p>This facade is used within a Java-SE VM to programmatically deploy REST services. Programmatic server-startup is solely
 * required in Java-SE, as any Java-EE engine must ship a built-in HTTP server implementation combined with an XML-based
 * configuration. The server factory class used is Jersey-specific, while the HTTP server class used is JDK-specific.
 * There are plenty HTTP server types more suitable for production environments, such as Apache Tomcat, Grizzly, Simple, etc;
 * however, they all require a learning curve for successful configuration, while this design auto-configures itself as long as
 * the package of the service classes matches this class's package.</p>
 * <p>Note that for LAZY fetching of entities within <i>EclipseLink</i> (dynamic weaving), add this to the JVM start parameters:
 * -javaagent:[path]eclipselink.jar</p>
 */
@Copyright(year=2013, holders="Sascha Baumeister")
public final class HttpContainer {
	static private final String CONFIGURATION_FILENAME = "components.properties";
	static private final int DEFAULT_PORT = 8010;
	static private final String DEFAULT_RESOURCE_DIRECTORY = "WEB-INF";
	static private final String DEFAULT_KEY_STORE_PASSWORD = "changeit";


	/**
	 * Modify the default uncaught exception handler to terminate programs
	 * that feature threads that do not catch errors except ThreadDeath.
	 */
	static {
		Thread.setDefaultUncaughtExceptionHandler((final Thread thread, final Throwable exception) -> {
			exception.printStackTrace(System.err);
			if (exception instanceof Error) System.exit(-1);
		});
	}


	/**
	 * Prevents external instantiation.
	 */
	private HttpContainer () {}


	/**
	 * Application entry point. The given arguments are expected to be an optional service socket address
	 * consisting of either a hostname:port combination or solely the port (default is 8045), an optional
	 * external resource directory path (default is the VM temporary directory), an optional key-store
	 * file path for HTTPS (default is {@code null} for HTTP), a key store password (default is"changeit"),
	 * and an optional key store device password (default is {@code null}).
	 * @param args the runtime arguments
	 * @throws IllegalArgumentException if the given service port is not a valid port number, or if a given component class is not valid
	 * @throws IllegalStateException if the local host name contains illegal characters
	 * @throws NotDirectoryException if the given external resource directory path is not a directory
	 * @throws NoSuchFileException if the given key store file path is neither {@code null} nor representing a regular file
	 * @throws ClassNotFoundException if a configured class cannot be found
	 * @throws IOException if there is an I/O related problem
	 * @throws GeneralSecurityException if none of the installed providers supports the specified key store file type,
	 * 			if any of the given passwords is invalid, if any of the certificates within the key store could not be
	 *  		loaded, or if the key store has expired
	 */
	static public void main (final String[] args) throws IllegalArgumentException, IllegalStateException, ClassNotFoundException, NotDirectoryException, NoSuchFileException, IOException, GeneralSecurityException {
		final String socketAddress = args.length > 0 && !args[0].isBlank() ? args[0].trim() : Integer.toString(DEFAULT_PORT);
		final String serviceHostname = socketAddress.contains(":") ? socketAddress.substring(0, socketAddress.indexOf(':')) : TcpServers.localAddress().getCanonicalHostName();
		final int servicePort = Integer.parseInt(socketAddress.contains(":") ? socketAddress.substring(socketAddress.indexOf(':') + 1) : socketAddress);
		final Path internalResourceDirectory = Thread.currentThread().getContextClassLoader().getResourceAsStream(DEFAULT_RESOURCE_DIRECTORY) == null ? null : ZipPaths.get(DEFAULT_RESOURCE_DIRECTORY);
		final Path externalResourceDirectory = args.length > 1 && !args[1].isBlank() ? Paths.get(args[1].trim()).toAbsolutePath() : null;
		final Path keyStorePath = args.length > 2 && !args[2].isBlank() ? Paths.get(args[2].trim()).toAbsolutePath() : null;
		final String keyStorePassword = args.length > 3 && !args[3].isBlank() ? args[3].trim() : DEFAULT_KEY_STORE_PASSWORD;
		final String keyStoreDevicePassword = args.length > 4 && !args[4].isBlank() ? args[4].trim() : null;

		// if (!TcpServers.localHostnames().anyMatch(hostname -> hostname.startsWith(serviceHostname))) throw new IllegalArgumentException("configured service host name is illegal: " + serviceHostname);
		if (servicePort < 1 || servicePort > 65535) throw new IllegalArgumentException("configured service port is illegal: " + servicePort);
		if (externalResourceDirectory != null && !Files.isDirectory(externalResourceDirectory)) throw new NotDirectoryException(externalResourceDirectory.toString());

		// Create HTTP/HTTPS container configuration
		final String serviceProtocol = keyStorePath == null ? "http" : "https";
		final URI serviceURI = URI.create(serviceProtocol + "://" + serviceHostname + ":" + servicePort + "/services");
		if (serviceURI.getHost() == null) throw new IllegalStateException("service host name contains illegal characters: " + serviceHostname);
		final ResourceConfig configuration = newResourceConfig();
		final SSLContext tlsContext = keyStorePath == null ? null : TcpServers.newTLSContext(keyStorePath, keyStorePassword, keyStoreDevicePassword);
		if (tlsContext != null) tlsContext.createSSLEngine(serviceURI.getHost(), serviceURI.getPort());

		// Create and start HTTP/HTTPS container
		final HttpServer httpContainer = JdkHttpServerFactory.createHttpServer(serviceURI, configuration, tlsContext);
		final HttpResourceHandler internalFileHandler = internalResourceDirectory == null ? null : new HttpResourceHandler("/internal", internalResourceDirectory);
		final HttpResourceHandler externalFileHandler = externalResourceDirectory == null ? null : new HttpResourceHandler("/external", externalResourceDirectory);
		if (internalFileHandler != null)
			httpContainer.createContext(internalFileHandler.getContextPath(), internalFileHandler);
		if (externalFileHandler != null)
			httpContainer.createContext(externalFileHandler.getContextPath(), externalFileHandler);

		try {
			System.out.format("%nWeb container running on origin \"%s://%s:%s\".%n", serviceURI.getScheme(), serviceURI.getHost(), serviceURI.getPort());
			System.out.format("Context path \"%s\" is configured for REST service access.%n", serviceURI.getPath());
			if (internalFileHandler != null)
				System.out.format("Context path \"%s\" is configured for class loader access within \"%s\".%n", internalFileHandler.getContextPath(), internalFileHandler.getResourceDirectory());
			if (externalFileHandler != null)
				System.out.format("Context path \"%s\" is configured for file system access within \"%s\".%n", externalFileHandler.getContextPath(), externalFileHandler.getResourceDirectory());
			System.out.println("Enter \"quit\" to terminate.");

			final BufferedReader charSource = new BufferedReader(new InputStreamReader(System.in));
			while (!"quit".equals(charSource.readLine()));
		} finally {
			httpContainer.stop(0);
		}
	}


	/**
	 * Creates and returns a new resource configuration.
	 * @return the resource configuration created
	 * @throws ClassNotFoundException if a configured class cannot be found
	 * @throws IOException if there is an I/O related problem
	 */
	static private ResourceConfig newResourceConfig () throws ClassNotFoundException, IOException {
		final ResourceConfig configuration = new ResourceConfig();

		try (InputStream byteSource = HttpContainer.class.getResourceAsStream(CONFIGURATION_FILENAME)) {
			final Properties properties = new Properties();
			properties.load(byteSource);

			for (final Map.Entry<?,?> entry : properties.entrySet()) {
				final String key = entry.getKey().toString().trim(), value = entry.getValue().toString().trim();

				try {
					final Class<?> componentClass = Class.forName(key, true, Thread.currentThread().getContextClassLoader());
					System.out.format("Configuring component %s%n", componentClass.getName());

					if (value.isEmpty()) {
						configuration.register(componentClass);
					} else {
						try {
							final Constructor<?> constructor = componentClass.getDeclaredConstructor(String.class);
							final Object componentInstance = constructor.newInstance(value);
							configuration.register(componentInstance);
						} catch (final Exception e) {
							throw new IllegalArgumentException(e);
						}
					}
				} catch (final ClassNotFoundException e) {
					if (!key.contains(".")) throw e;

					final Class<?> componentClass = Class.forName(key.substring(0, key.lastIndexOf('.')), true, Thread.currentThread().getContextClassLoader());
					try {
						final Field field = componentClass.getDeclaredField(key.substring(key.lastIndexOf('.') + 1));
						if (!Modifier.isStatic(field.getModifiers()) | Modifier.isFinal(field.getModifiers()) | field.getType() != String.class) throw new IllegalArgumentException(key);

						System.out.format("Configuring class variable %s in %s to value \"%s\"%n", field.getName(), componentClass, value);
						field.setAccessible(true);
						field.set(null, value);
					} catch (final Exception ne) {
						throw new IllegalArgumentException(ne);
					}
				}
			}
		}

		return configuration;
	}
}