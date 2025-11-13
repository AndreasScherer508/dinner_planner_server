package edu.sb.dinner_planner.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import edu.sb.tool.Copyright;


/**
 * Facade for TCP server related operations.
 */
@Copyright(year=2014, holders="Sascha Baumeister")
public class TcpServers {

	/**
	 * Prevents external instantiation.
	 */
	private TcpServers () {}

	
	/**
	 * Returns the local address.
	 * @return the local address, or the loopback address if there is no local address
	 */
	static public InetAddress localAddress () {
		try {
			return InetAddress.getLocalHost();
		} catch (final UnknownHostException exception) {
			return InetAddress.getLoopbackAddress();
		}
	}


	/**
	 * Returns a {@code Stream} of all valid host names of the local computer.
	 * The {@code Stream} contains at least the host name {@code "localhost"}.
	 * @return the valid host names of this computer
	 */
	static public Stream<String> localHostnames () {
		final Set<String> hostnames = new TreeSet<>(Arrays.asList("localhost"));
		try {
			final List<NetworkInterface> networkInterfaces = NetworkInterface.networkInterfaces().collect(Collectors.toList());
			for (final NetworkInterface networkInterface : networkInterfaces) {
				final String hostname = networkInterface.inetAddresses().map(networkAddress -> networkAddress.getHostName()).findAny().orElse(null);
				if (hostname == null || hostname.isEmpty() || hostname.contains(":") || Character.isDigit(hostname.charAt(0))) continue;

				final int dotPosition = hostname.indexOf('.');
				if (dotPosition != -1) hostnames.add(hostname.substring(0, dotPosition));
				hostnames.add(hostname);
			}
		} catch (final SocketException e) {
			// do nothing
		}

		return hostnames.stream();
	}


	/**
	 * Returns a new TLS context based on a JKS key store and the most recent supported transport layer security (TLS) version.
	 * @param keyStorePath the key store file path
	 * @param keyStorePassword the password for recovering keys from the associated key store
	 * @param keyStoreDevicePassword the (optional) device password used to unlock the associated key store, or {@code null} for none
	 * @return the TLS context created
	 * @throws NullPointerException if the given keyStorePath or keyStorePassword is {@code null}
	 * @throws NoSuchFileException if the given keyStorePath is not representing a regular file
	 * @throws IOException if there is an I/O related problem
	 * @throws GeneralSecurityException if none of the installed providers supports the specified key store file type,
	 * 			if any of the given passwords is invalid, if any of the certificates within the key store could not be
	 *  		loaded, or if the key store has expired
	 */
	static public SSLContext newTLSContext (final Path keyStorePath, final String keyStorePassword, final String keyStoreDevicePassword) throws NullPointerException, NoSuchFileException, AccessDeniedException, IOException, GeneralSecurityException {
		if (keyStorePath == null | keyStorePassword == null) throw new NullPointerException();
		if (!Files.isRegularFile(keyStorePath)) throw new NoSuchFileException(keyStorePath.toString());

		final KeyStore keyStore = KeyStore.getInstance(keyStorePath.toFile(), keyStoreDevicePassword == null ? null : keyStoreDevicePassword.toCharArray());
		try (InputStream byteSource = Files.newInputStream(keyStorePath)) {
			keyStore.load(byteSource, keyStorePassword.toCharArray());
		} catch (final IOException exception) {
			if (exception.getCause() instanceof GeneralSecurityException) throw (GeneralSecurityException) exception.getCause();
			throw exception;
		}

		final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

		final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(keyStore);

		final SSLContext context = SSLContext.getInstance("TLS");
		context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

		return context;
	}


	/**
	 * Returns a new HTTP server instance for either the HTTPS or the HTTP protocol.
	 * @param serviceAddress the service address
	 * @param tlsContext the TLS context for HTTPS, or {@code null} for HTTP
	 * @return the HTTP/HTTPS server created
	 * @throws NullPointerException if the given service address is {@code null}
	 * @throws IOException if there is an I/O related problem
	 */
	static public HttpServer newHttpServer (final InetSocketAddress serviceAddress, final SSLContext tlsContext) throws NullPointerException, IOException {
		if (serviceAddress == null) throw new NullPointerException();
		if (tlsContext == null) return HttpServer.create(serviceAddress, 0);

		final HttpsConfigurator configurator = new HttpsConfigurator(tlsContext) {
			public void configure (final HttpsParameters parameters) {
				parameters.setWantClientAuth(false);
				parameters.setNeedClientAuth(false);
				super.configure(parameters);
			}
		};

		final HttpsServer server = HttpsServer.create(serviceAddress, 0);
		server.setHttpsConfigurator(configurator);
		return server;
	}
}