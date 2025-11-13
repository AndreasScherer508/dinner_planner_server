package edu.sb.dinner_planner.rdbms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import edu.sb.dinner_planner.persistence.AbstractEntity;
import edu.sb.dinner_planner.persistence.Document;
import edu.sb.dinner_planner.persistence.Ingredient;
import edu.sb.dinner_planner.persistence.Person;
import edu.sb.dinner_planner.persistence.Recipe;
import edu.sb.dinner_planner.persistence.Victual;
import edu.sb.tool.CommandShell;
import edu.sb.tool.Copyright;
import edu.sb.tool.HashCodes;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.persistence.Cache;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.TypedQuery;
import jakarta.validation.Validation;
import jakarta.validation.Validator;


/**
 * Persistence administration controller type.
 */
@Copyright(year=2022, holders="Sascha Baumeister")
public class PersistenceAdministrationController {
	static private final Jsonb JSON_MARSHALER = JsonbBuilder.create();
	static private final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
	static private final String QUERY_ENTITIES = "select e from AbstractEntity as e";
	static private final String QUERY_DOCUMENT_BY_HASH = "select d from Document as d where d.hash = :hash";

	private final CommandShell terminal;
	private final EntityManagerFactory entityManagerFactory;


	public PersistenceAdministrationController () {
		this.terminal = new CommandShell();
		this.entityManagerFactory = Persistence.createEntityManagerFactory("local_database");

		this.terminal.setDefaultEventListener(event -> this.processHelpCommand(event.arguments()));
		this.terminal.addEventListener("quit", event -> this.processQuitCommand(event.arguments()));
		this.terminal.addEventListener("exit", event -> this.processQuitCommand(event.arguments()));
		this.terminal.addEventListener("query-entities", event -> this.processQueryEntitiesCommand(event.arguments()));
		this.terminal.addEventListener("insert-person", event -> this.processInsertPersonCommand(event.arguments()));
		this.terminal.addEventListener("update-person", event -> this.processUpdatePersonCommand(event.arguments()));
		this.terminal.addEventListener("delete-person", event -> this.processDeletePersonCommand(event.arguments()));
		this.terminal.addEventListener("insert-document", event -> this.processInsertOrUpdateDocumentCommand(event.arguments()));
		this.terminal.addEventListener("delete-document", event -> this.processDeleteDocumentCommand(event.arguments()));
		this.terminal.addEventListener("insert-recipe", event -> this.processInsertRecipeCommand(event.arguments()));
		this.terminal.addEventListener("update-recipe", event -> this.processUpdateRecipeCommand(event.arguments()));
		this.terminal.addEventListener("delete-recipe", event -> this.processDeleteRecipeCommand(event.arguments()));
		this.terminal.addEventListener("add-ingredient", event -> this.processCreateIngredientCommand(event.arguments()));
		this.terminal.addEventListener("remove-ingredient", event -> this.processDeleteIngredientCommand(event.arguments()));
		this.terminal.addEventListener("add-illustration", event -> this.processAddIllustrationCommand(event.arguments()));
		this.terminal.addEventListener("remove-illustration", event -> this.processRemoveIllustrationCommand(event.arguments()));
	}


	public CommandShell terminal () {
		return this.terminal;
	}


	public EntityManagerFactory entityManagerFactory () {
		return this.entityManagerFactory;
	}


	private void processQuitCommand (final String arguments) throws IOException {
		throw new CommandShell.AbortException();
	}


	private void processHelpCommand (final String arguments) {
		System.out.println("Available commands:");
		System.out.println("- exit: Terminates this program");
		System.out.println("- quit: Terminates this program");
		System.out.println("- help: Displays this command list");
		System.out.println("- query-entities: Queries and displays all entities");
		System.out.println("- insert-person <JSON>: Inserts a new person into the database");
		System.out.println("- update-person <JSON>: Updates an existing person within the database");
		System.out.println("- delete-person <person-ID>: Deletes an existing person from the database");
		System.out.println("- insert-document <file-path>: Inserts/Updates a document within the database");
		System.out.println("- delete-document <document-ID>: Deletes an existing document from the database");
		System.out.println("- insert-recipe <JSON>: Inserts a new recipe into the database");
		System.out.println("- update-recipe <JSON>: Updates an existing recipe within the database");
		System.out.println("- delete-recipe <recipe-ID>: Deletes an existing recipe from the database");
		System.out.println("- add-ingredient <JSON>: Inserts a new ingredient into the database");
		System.out.println("- remove-ingredient <ingredient-ID>: Deletes an existing ingredient from the database");
		System.out.println("- add-illustration <recipe-ID> <document-ID>: Associates the given recipe with the given document");
		System.out.println("- remove-illustration <recipe-ID> <document-ID>: Disassociates the given recipe from the given document");
	}


	private void processQueryEntitiesCommand (final String arguments) {
		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();
			try {
				final TypedQuery<AbstractEntity> query = entityManager.createQuery(QUERY_ENTITIES, AbstractEntity.class);

				System.out.println("Available entities:");
				query.getResultStream().forEach(entity -> System.out.println(JSON_MARSHALER.toJson(entity)));
				System.out.println();
			} finally {
				entityManager.getTransaction().rollback();
			}
		}
	}


	private void processInsertPersonCommand (final String arguments) {
		final Person person = JSON_MARSHALER.fromJson(arguments, Person.class);
		if (person.getIdentity() != 0) throw new IllegalArgumentException("JSON person identity must be zero!");
		if (!VALIDATOR.validate(person).isEmpty()) throw new IllegalArgumentException("JSON person data must be valid!");
		final long avatarIdentity = person.getAvatar() == null ? 1L : person.getAvatar().getIdentity();

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();
			try {
				final Document avatar = entityManager.find(Document.class, avatarIdentity);
				if (avatar == null) throw new IllegalArgumentException("avatar not found!");
				person.setAvatar(avatar);

				entityManager.persist(person);

				entityManager.getTransaction().commit();
				System.out.println("Inserted new person with ID " + person.getIdentity());
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		}
	}


	private void processUpdatePersonCommand (final String arguments) {
		try {
			final Person personTemplate = JSON_MARSHALER.fromJson(arguments, Person.class);

			try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
				entityManager.getTransaction().begin();

				final Person person = entityManager.find(Person.class, personTemplate.getIdentity());
				if (person == null) throw new IllegalArgumentException("person not found!");

				person.setModified(System.currentTimeMillis());
				if (personTemplate.getVersion() > 1) person.setVersion(personTemplate.getVersion());
				if (personTemplate.getEmail() != null) person.setEmail(personTemplate.getEmail());
				if (personTemplate.getGroup() != null) person.setGroup(personTemplate.getGroup());
				if (personTemplate.getName().getTitle() != null) person.getName().setTitle(personTemplate.getName().getTitle());
				if (personTemplate.getName().getFamily() != null) person.getName().setFamily(personTemplate.getName().getTitle());
				if (personTemplate.getName().getGiven() != null) person.getName().setGiven(personTemplate.getName().getTitle());
				if (personTemplate.getAddress().getPostcode() != null) person.getAddress().setPostcode(personTemplate.getAddress().getPostcode());
				if (personTemplate.getAddress().getStreet() != null) person.getAddress().setStreet(personTemplate.getAddress().getStreet());
				if (personTemplate.getAddress().getCity() != null) person.getAddress().setCity(personTemplate.getAddress().getCity());
				if (personTemplate.getAddress().getCountry() != null) person.getAddress().setCountry(personTemplate.getAddress().getCountry());
				person.getPhones().retainAll(personTemplate.getPhones());	// DELETE only required related entries in association table
				person.getPhones().addAll(personTemplate.getPhones());		// INSERT only required related entries in association table

				final Number avatarReference = (Number) personTemplate.getAttributes().get("avatar-reference");
				if (avatarReference != null) {
					final Document avatar = entityManager.find(Document.class, avatarReference.longValue());
					if (avatar == null) throw new IllegalArgumentException("avatar not found!");
					person.setAvatar(avatar);
				}

				try {
					entityManager.flush();

					entityManager.getTransaction().commit();
					System.out.println("Updated existing person with ID " + person.getIdentity());
				} finally {
					if (entityManager.getTransaction().isActive())
						entityManager.getTransaction().rollback();
				}
			}
		} catch (final ClassCastException e) {
			throw new IllegalArgumentException("Invalid property type encountered!", e);
		}
	}


	private void processDeletePersonCommand (final String arguments) {
		final long personIdentity = Long.parseLong(arguments);

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();
			try {
				final Person person = entityManager.find(Person.class, personIdentity);
				if (person == null) throw new IllegalArgumentException("person not found!");

				entityManager.remove(person);

				entityManager.getTransaction().commit();
				System.out.println("Deleted existing person with ID " + person.getIdentity());
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		}
	}


	private void processInsertOrUpdateDocumentCommand (final String arguments) throws IOException {
		final Path documentPath = Paths.get(arguments);
		final byte[] documentContent = Files.readAllBytes(documentPath);
		final String documentHash = HashCodes.sha2HashText(256, documentContent);
		final String documentType = Files.probeContentType(documentPath);
		final String documentDescription = documentPath.getFileName().toString();

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();
			try {
				final TypedQuery<Document> query = entityManager.createQuery(QUERY_DOCUMENT_BY_HASH, Document.class);
				final Document document = query
					.setParameter("hash", documentHash)
					.getResultStream()
					.findAny()
					.orElseGet(() -> new Document(documentContent));

				document.setModified(System.currentTimeMillis());
				document.setType(documentType);
				document.setDescription(documentDescription);

				if (document.getIdentity() == 0L)
					entityManager.persist(document);
				else
					entityManager.flush();

				entityManager.getTransaction().commit();
				System.out.println("Inserted/Updated document with ID " + document.getIdentity());
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		}
	}


	private void processDeleteDocumentCommand (final String arguments) {
		final long documentIdentity = Long.parseLong(arguments);

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();
			try {
				final Document document = entityManager.find(Document.class, documentIdentity);
				if (document == null) throw new IllegalArgumentException("document not found!");

				entityManager.remove(document);

				entityManager.getTransaction().commit();
				System.out.println("Deleted existing document with ID " + document.getIdentity());
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		}
	}

	
	private void processInsertRecipeCommand (final String arguments) {
		final Recipe recipe = JSON_MARSHALER.fromJson(arguments, Recipe.class);
		if (recipe.getIdentity() != 0) throw new IllegalArgumentException("JSON recipe identity must be zero!");

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();

			final Number avatarReference = (Number) recipe.getAttributes().get("avatar-reference");
			if (avatarReference != null) {
				final Document avatar = entityManager.find(Document.class, avatarReference.longValue());
				if (avatar == null) throw new IllegalArgumentException("avatar not found!");
				recipe.setAvatar(avatar);
			}

			final Number authorReference = (Number) recipe.getAttributes().get("author-reference");
			if (authorReference != null) {
				final Person author = entityManager.find(Person.class, authorReference.longValue());
				if (author == null) throw new IllegalArgumentException("author not found!");
				recipe.setAuthor(author);
			}

			try {
				entityManager.persist(recipe);

				entityManager.getTransaction().commit();
				System.out.println("Inserted new recipe with ID " + recipe.getIdentity());
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		} catch (final ClassCastException e) {
			throw new IllegalArgumentException("Invalid property type encountered!", e);
		}
	}


	private void processUpdateRecipeCommand (final String arguments) {
		try {
			final Recipe recipeTemplate = JSON_MARSHALER.fromJson(arguments, Recipe.class);

			try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
				entityManager.getTransaction().begin();

				final Recipe recipe = entityManager.find(Recipe.class, recipeTemplate.getIdentity());
				if (recipe == null) throw new IllegalArgumentException("recipe not found!");

				recipe.setModified(System.currentTimeMillis());
				if (recipeTemplate.getVersion() > 1) recipe.setVersion(recipeTemplate.getVersion());
				if (recipeTemplate.getCategory() != null) recipe.setCategory(recipeTemplate.getCategory());
				if (recipeTemplate.getTitle() != null) recipe.setTitle(recipeTemplate.getTitle());
				if (recipeTemplate.getDescription() != null) recipe.setDescription(recipeTemplate.getDescription());
				if (recipeTemplate.getInstruction() != null) recipe.setInstruction(recipeTemplate.getInstruction());

				final Number avatarReference = (Number) recipe.getAttributes().get("avatar-reference");
				if (avatarReference != null) {
					final Document avatar = entityManager.find(Document.class, avatarReference.longValue());
					if (avatar == null) throw new IllegalArgumentException("avatar not found!");
					recipe.setAvatar(avatar);
				}

				final Number authorReference = (Number) recipe.getAttributes().get("author-reference");
				if (authorReference != null) {
					final Person author = entityManager.find(Person.class, authorReference.longValue());
					if (author == null) throw new IllegalArgumentException("author not found!");
					recipe.setAuthor(author);
				}

				try {
					entityManager.flush();

					entityManager.getTransaction().commit();
					System.out.println("Updated existing recipe with ID " + recipe.getIdentity());
				} finally {
					if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
				}
			}
		} catch (final ClassCastException e) {
			throw new IllegalArgumentException("Invalid property type encountered!", e);
		}
	}


	private void processDeleteRecipeCommand (final String arguments) {
		final long recipeIdentity = Long.parseLong(arguments);

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();
			try {
				final Recipe recipe = entityManager.find(Recipe.class, recipeIdentity);
				if (recipe == null) throw new IllegalArgumentException("recipe not found!");

				entityManager.remove(recipe);

				entityManager.getTransaction().commit();
				System.out.println("Deleted existing recipe with ID " + recipe.getIdentity());
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		}
	}


	private void processCreateIngredientCommand (final String arguments) {
		final Ingredient ingredientTemplate = JSON_MARSHALER.fromJson(arguments, Ingredient.class);

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();

			final Number recipeReference = (Number) ingredientTemplate.getAttributes().get("recipe-reference");
			if (recipeReference == null) throw new IllegalArgumentException("recipe not found!");
			final Recipe recipe = entityManager.find(Recipe.class, recipeReference.longValue());
			if (recipe == null) throw new IllegalArgumentException("recipe not found!");
			final Victual victual = entityManager.find(Victual.class, ingredientTemplate.getVictual().getIdentity());
			if (victual == null) throw new IllegalArgumentException("victual not found!");

			final Ingredient ingredient = new Ingredient(recipe);
			ingredient.setVictual(victual);
			ingredient.setAmount(ingredientTemplate.getAmount());
			ingredient.setUnit(ingredientTemplate.getUnit());

			try {
				entityManager.persist(ingredient);

				entityManager.getTransaction().commit();
				final Cache secondLevelCache = entityManager.getEntityManagerFactory().getCache();
				secondLevelCache.evict(Recipe.class, recipe.getIdentity());
				System.out.println("Inserted new ingredient with ID " + ingredient.getIdentity());
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		} catch (final ClassCastException e) {
			throw new IllegalArgumentException("Invalid property type encountered!", e);
		}
	}


	private void processDeleteIngredientCommand (final String arguments) {
		final long ingredientIdentity = Long.parseLong(arguments);

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();
			try {
				final Ingredient ingredient = entityManager.find(Ingredient.class, ingredientIdentity);
				if (ingredient == null) throw new IllegalArgumentException("ingredient not found!");

				entityManager.remove(ingredient);

				entityManager.getTransaction().commit();
				System.out.println("Removed existing ingredient with ID " + ingredient.getIdentity());
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		}
	}


	
	private void processAddIllustrationCommand (final String arguments) {
		final int delimiterPosition = arguments.indexOf(' ');
		if (delimiterPosition == -1) throw new IllegalArgumentException("recipe identity AND document identity must be given!");
		final long recipeIdentity = Long.parseLong(arguments.substring(0, delimiterPosition).trim());
		final long documentIdentity = Long.parseLong(arguments.substring(delimiterPosition + 1).trim());

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();

			final Recipe recipe = entityManager.find(Recipe.class, recipeIdentity);
			if (recipe == null) throw new IllegalArgumentException("recipe not found!");

			final Document document = entityManager.find(Document.class, documentIdentity);
			if (document == null) throw new IllegalArgumentException("document not found!");

			recipe.getIllustrations().add(document);

			try {
				entityManager.flush();

				entityManager.getTransaction().commit();

				System.out.println("Added document " + document.getIdentity() + " to recipe " + recipe.getIdentity() + "'s illustrations!");
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		}
	}


	private void processRemoveIllustrationCommand (final String arguments) {
		final int delimiterPosition = arguments.indexOf(' ');
		if (delimiterPosition == -1) throw new IllegalArgumentException("recipe identity AND document identity must be given!");
		final long recipeIdentity = Long.parseLong(arguments.substring(0, delimiterPosition).trim());
		final long documentIdentity = Long.parseLong(arguments.substring(delimiterPosition + 1).trim());

		try (EntityManager entityManager = this.entityManagerFactory.createEntityManager()) {
			entityManager.getTransaction().begin();

			final Recipe recipe = entityManager.find(Recipe.class, recipeIdentity);
			if (recipe == null) throw new IllegalArgumentException("recipe not found!");

			final Document document = entityManager.find(Document.class, documentIdentity);
			if (document == null) throw new IllegalArgumentException("document not found!");

			recipe.getIllustrations().remove(document);

			try {
				entityManager.flush();

				entityManager.getTransaction().commit();

				System.out.println("Removed document " + document.getIdentity() + " from recipe " + recipe.getIdentity() + "'s illustrations!");
			} finally {
				if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback();
			}
		}
	}
}