package edu.sb.dinner_planner.server;

import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.sb.tool.Copyright;
import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;


/**
 * This exception mapper maps {@link WebApplicationException} instances to their respective HTTP responses, while all other
 * exception types are mapped to HTTP 500 Internal Server Error. The exceptions are additionally logged, with a log level
 * appropriate for the exception's severity.
 */
@Provider
@Priority(Priorities.USER)
@Copyright(year=2013, holders="Sascha Baumeister")
public class RestResponseCodeProvider implements ExceptionMapper<Throwable> {

	/**
	 * Maps the given exception to a HTTP response. In case of a WebApplicationException instance, it's associated response is
	 * returned. Otherwise, a generic HTTP 500 response is returned. The exceptions are logged using a log level that
	 * corresponds to their severity:
	 * <ul>
	 * <li>code 5xx (server side error): Level.WARNING</li>
	 * <li>code 4xx (client side error): Level.INFO</li>
	 * <li>code 3xx (redirection): Level.FINE</li>
	 * <li>code 2xx (success): Level.FINER</li>
	 * <li>code 1xx (informational): Level.FINEST</li>
	 * </ul>
	 * @param exception the exception to be mapped
	 * @return the mapped response
	 * @throws NullPointerException if the given argument is null
	 * @throws Error if the given argument is an error
	 */
	public Response toResponse (final Throwable exception) throws NullPointerException, Error {
		if (exception instanceof Error) throw (Error) exception;

		final Response response = exception instanceof WebApplicationException ? ((WebApplicationException) exception).getResponse() : Response.status(INTERNAL_SERVER_ERROR).build();
		Logger.getGlobal().log(logLevel(response.getStatusInfo()), exception.getMessage(), exception);

		final List<Throwable> exceptionChain = new ArrayList<>();
		for (Throwable cause = exception; cause != null && !exceptionChain.contains(cause); cause = cause.getCause())
			exceptionChain.add(cause);
		exceptionChain.stream()
			.filter(cause -> cause instanceof ConstraintViolationException)
			.map(cause -> (ConstraintViolationException) cause)
			.flatMap(cause -> cause.getConstraintViolations().stream())
			.forEach(constraintViolation -> Logger.getGlobal().log(Level.INFO, constraintViolation.toString()));

		return response;
	}


	/**
	 * Returns the log level appropriate for the given HTTP response status.
	 * @param status the HTTP response status
	 * @return the log level
	 * @throws NullPointerException if the given status is null
	 */
	static private Level logLevel (final Response.StatusType status) throws NullPointerException {
		switch (status.getFamily()) {
			case SERVER_ERROR:
				return Level.WARNING;
			case CLIENT_ERROR:
				return Level.INFO;
			case REDIRECTION:
				return Level.FINE;
			case SUCCESSFUL:
				return Level.FINER;
			case INFORMATIONAL:
				return Level.FINEST;
			default:
				throw new AssertionError();
		}
	}
}