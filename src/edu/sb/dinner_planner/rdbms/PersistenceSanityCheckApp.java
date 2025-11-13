package edu.sb.dinner_planner.rdbms;

import java.util.List;
import java.util.Set;
import edu.sb.dinner_planner.persistence.AbstractEntity;
import edu.sb.tool.Copyright;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;


/**
 * JPA sanity-check non-interactive text application for persistence unit "local_database".
 */
@Copyright(year=2022, holders="Sascha Baumeister")
public class PersistenceSanityCheckApp {
	static private final String QUERY_ENTITIES = "select e from AbstractEntity as e where e.identity >= :minIdentity and e.identity <= :maxIdentity";
	static public final EntityManagerFactory ENTITY_MANAGER_FACTORY = Persistence.createEntityManagerFactory("local_database");
	static private final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
	static private final Jsonb JSON_MARSHALER = JsonbBuilder.create();


	/**
	 * Application entry point.
	 * @param args the runtime arguments
	 */
	static public void main (final String[] args) {
		final boolean jsonMode = args.length == 0 ? false : Boolean.parseBoolean(args[0]);
		final long minIdentity = args.length <= 1 ? 1L : Long.parseLong(args[1]);
		final long maxIdentity = args.length <= 2 ? Long.MAX_VALUE : Long.parseLong(args[2]);

		try (EntityManager entityManager = ENTITY_MANAGER_FACTORY.createEntityManager()) {
			final List<AbstractEntity> entities = entityManager
				.createQuery(QUERY_ENTITIES, AbstractEntity.class)
				.setParameter("minIdentity", minIdentity)
				.setParameter("maxIdentity", maxIdentity)
				.getResultList();

			System.out.println("entities (class & ID data only):");
			for (final AbstractEntity entity : entities)
				System.out.println(entity);
			System.out.println();

			System.out.println("entity validation problems:");
			final Validator validator = VALIDATOR_FACTORY.getValidator();
			boolean noValidationProblems = true;
			for (final AbstractEntity entity : entities) {
				final Set<ConstraintViolation<AbstractEntity>> constraintViolations = validator.validate(entity);
				noValidationProblems &= constraintViolations.isEmpty();
				if (!constraintViolations.isEmpty()) System.out.println(entity + ": " + constraintViolations);
			}
			if (noValidationProblems) System.out.println("none");
			System.out.println();

			if (jsonMode) {
				System.out.println("entities (JSON):");
				for (final AbstractEntity entity : entities)
					System.out.println(JSON_MARSHALER.toJson(entity));
				System.out.println();
			}
		}
	}
}