open module edu.sb.dinner_planner.server {
	requires edu.sb.dinner_planner.model;

	requires java.instrument;
	requires jdk.httpserver;
	requires jakarta.el;
	requires jakarta.activation;
	requires jakarta.annotation;
	requires jakarta.persistence;
	requires jersey.container.jdk.http;

	requires jakarta.inject;
	requires org.glassfish.hk2.api;
	requires java.desktop;
	requires jakarta.json.bind;
	requires jakarta.validation;
}