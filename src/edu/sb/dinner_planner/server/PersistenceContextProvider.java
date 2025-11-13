package edu.sb.dinner_planner.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import edu.sb.tool.Copyright;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.ext.Provider;


/**
 * HK2 based binder providing {@link PersistenceContext} annotation based
 * dependency injection for {@code JPA} {@link EntityManager} instances
 * for configured components of Java SE based {@code JAX-RS} servers.
 */
@Provider
@Copyright(year=2025, holders={"Felix Dietrich", "Sascha Baumeister"})
public class PersistenceContextProvider extends AbstractBinder {
	static private final Map<String,EntityManagerFactory> ENTITY_MANAGER_FACTORY_CACHE = new ConcurrentHashMap<>();


	/**
	 * Performs the required binding definitions.
	 */
	@Override
	protected void configure () {
		final TypeLiteral<InjectionResolver<PersistenceContext>> typeLiteral = new TypeLiteral<>() {};
		this.bind(PersistenceContextInjectionResolver.class).to(typeLiteral).in(Singleton.class);
		this.bind(EntityManagerMap.class).to(EntityManagerMap.class).in(RequestScoped.class).proxy(true);
	}



	/**
	 * Singleton type providing an {@code HK2} injection target for the {@code JPA}
	 * {@link PersistenceContext} instance variable scoped annotation.
	 */
	@Singleton
	static private class PersistenceContextInjectionResolver implements InjectionResolver<PersistenceContext> {

		@Inject
		private EntityManagerMap entityManagerCache;


		/**
		 * Decides whether or not the annotation that indicates that this is an
		 * injection point can appear in the parameter list of a constructor.
		 * @return false
		 */
		public boolean isConstructorParameterIndicator () {
			return false;
		}


		/**
		 * Decides whether or not the annotation that indicates that this is an
		 * injection point can appear in the parameter list of a method.
		 * @return false
		 */
		public boolean isMethodParameterIndicator () {
			return false;
		}


		/**
		 * Returns the object that should be injected into the given injection point. This operation does
		 * not perform the injection itself; however, it is responsible to ensure that the object returned
		 * can be safely injected into the injection point.
		 * @param injectee the injection point the returned value is being injected into
		 * @param rootClassServiceHandle the (optional) service handle of the root class being created,
		 *        which should be used in order to ensure proper destruction of associated scoped objects
		 * @return the value to be injected into the given injection point, which may be {@code null}
		 */
		public Object resolve (final Injectee injectee, final ServiceHandle<?> rootClassServiceHandle) {
			if (injectee.getRequiredType() != EntityManager.class | injectee.getParent() == null) return null;

			final PersistenceContext persistenceContextAnnotation = injectee.getParent().getAnnotation(PersistenceContext.class);
			return Proxy.newProxyInstance(EntityManager.class.getClassLoader(), new Class[] { EntityManager.class }, new EntityManagerInvocationHandler(persistenceContextAnnotation.unitName(), this.entityManagerCache));
		}
	}



	/**
	 * Invocation handler type for {@link EntityManager} proxy instances.
	 */
	static private class EntityManagerInvocationHandler implements InvocationHandler {
		private final String persistenceUnitName;
		private final Map<String,EntityManager> entityManagerCache;


		/**
		 * Initializes a new instance
		 * @param persistenceUnitName the persistence unit name
		 * @param entityManagerCache the entity manager cache
		 * @throws NullPointerException if any of the given arguments is {@code null}
		 */
		EntityManagerInvocationHandler(final String persistenceUnitName, final Map<String,EntityManager> entityManagerCache) throws NullPointerException {
			this.persistenceUnitName = Objects.requireNonNull(persistenceUnitName);
			this.entityManagerCache = Objects.requireNonNull(entityManagerCache);
		}


		/**
		 * {@inheritDoc}
		 * @throws IllegalStateException if the method to be called is the close() method.
		 * @throws IllegalAccessException if the given method is enforcing Java language access control and the underlying method is inaccessible
		 * @throws InvocationTargetException if the underlying method throws an exception
		 */
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws IllegalStateException, IllegalAccessException, InvocationTargetException {
			if (method.getName().equals("close")) throw new IllegalStateException("entity manager is container managed!");

			final EntityManager entityManager;
			synchronized (this.entityManagerCache) {
				if (!this.entityManagerCache.containsKey(this.persistenceUnitName)) {
					final EntityManagerFactory entityManagerFactory = ENTITY_MANAGER_FACTORY_CACHE.computeIfAbsent(this.persistenceUnitName, key -> Persistence.createEntityManagerFactory(key));
					this.entityManagerCache.put(this.persistenceUnitName, entityManagerFactory.createEntityManager());
				}

				entityManager = this.entityManagerCache.get(this.persistenceUnitName);	
			}

			return method.invoke(entityManager, args);
		}
	}



	/**
	 * HK2 based entity manager map type. Instances can work as entity manager caches bound
	 * to an HTTP request's life cycle; once the HTTP request is done, all entity managers
	 * contained within are disposed of.
	 */
	static class EntityManagerMap extends HashMap<String,EntityManager> implements PreDestroy {
		private static final long serialVersionUID = 1L;

		/**
		 * The component is about to be removed from the registry. This implies iterating over
		 * all entity managers contained within, rolling back active entity manager transactions
		 * if required, and subsequently closing the entity managers if required. Finally, the
		 * map is cleared.
		 */
		public void preDestroy () {
			for (final EntityManager entityManager : this.values()) {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
				if (entityManager.isOpen()) entityManager.close();
			}

			this.clear();
		}
	}
}
