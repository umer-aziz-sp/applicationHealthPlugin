package sailpoint.plugin.applicationhealthplugin.server;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.AbstractConnector;
import sailpoint.integration.IntegrationInterface;
import sailpoint.integration.Util;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Custom;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.QueryOptions;
import sailpoint.plugin.applicationhealthplugin.Constants;
import sailpoint.plugin.server.AbstractPluginBackgroundService;
import sailpoint.plugin.server.PluginEnvironment;
import sailpoint.tools.GeneralException;

public class ApplicationHealthService extends AbstractPluginBackgroundService {

	private static final Log log = LogFactory.getLog(ApplicationHealthService.class);

	/**
	 * Constructor
	 */
	public ApplicationHealthService() {
		super();
		log.debug("Constructor");
	}
	
	/**
	 * 
	 * @param objectType
	 * @param name
	 * @param oldStatus
	 * @param newStatus
	 */
	private void statusChangeAction(String objectType, String name, String oldStatus, String newStatus) {
		log.debug(String.format("Enter: statusChangeAction(%s, %s, %s, %s)", objectType, name, oldStatus, newStatus));
		// TODO - placeholder
	}

	/**
	 * Get a database connection from the plugin environment.
	 * 
	 * @return
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private Connection getDatabaseConnection() throws GeneralException, SQLException {
		log.debug("Enter: getDatabaseConnection()");
		PluginEnvironment environment = PluginEnvironment.getEnvironment();
		return environment.getJDBCConnection();
	}

	/**
	 * Get a list of Application objects configured in IdentityIQ to be tested.
	 * 
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	private Map<String, String> getIIQApplications(SailPointContext context) throws GeneralException {
		log.debug(String.format("Enter: getIIQApplications(%s)", context.toString()));
		Map<String, String> applications = new HashMap<String, String>();
		QueryOptions qo = new QueryOptions();
		Iterator<Object[]> iterator = context.search(Application.class, qo, "name,id");
		while (iterator.hasNext()) {
			Object[] record = iterator.next();
			String name = (String) record[0];
			String id = (String) record[1];
			log.trace(String.format("Application %s (%s)", name, id));
			applications.put(name, id);
		}
		return applications;
	}

	private Map<String, String> checkExclusions(SailPointContext context, String objectType, Map<String, String> map) throws GeneralException {
		log.debug(String.format("Enter: checkExclusions(%s, %s, %s)", context.toString(), objectType, map.toString()));
		Custom custom = context.getObjectByName(Custom.class, Constants.CUSTOM_CONFIG);
		if (custom != null) {
			String entryName = String.format("ignored%ss", objectType);
			List<?> list = custom.getList(entryName);
			if (list != null && !list.isEmpty()) {
				Set<String> keys = map.keySet();
				for (String key: keys) {
					if (list.contains(key)) {
						log.debug(String.format("Ignoring %s %s", objectType, key));
						map.remove(key);
					}
				}
			}
		}
		return map;
	}
	
	/**
	 * Get a list of IntegrationConfig objects configured in IdentityIQ to be
	 * tested.
	 * 
	 * @param context
	 * @return
	 * @throws GeneralException
	 */
	private Map<String, String> getIIQIntegrations(SailPointContext context) throws GeneralException {
		log.debug(String.format("Enter: getIIQIntegrations(%s)", context.toString()));
		Map<String, String> integrations = new HashMap<String, String>();
		QueryOptions qo = new QueryOptions();
		Iterator<Object[]> iterator = context.search(IntegrationConfig.class, qo, "name,id");
		while (iterator.hasNext()) {
			Object[] record = iterator.next();
			String name = (String) record[0];
			String id = (String) record[1];
			log.trace(String.format("Integration %s (%s)", name, id));
			integrations.put(name, id);
		}
		return checkExclusions(context, Constants.TYPE_INTEGRATION, integrations);
	}

	/**
	 * Remove entries from the database for applications that no longer exist in
	 * IdentityIQ.
	 * 
	 * @param set
	 * @throws SQLException
	 * @throws GeneralException
	 */
	private void cleanApplications(Set<String> set) throws GeneralException, SQLException {
		log.debug(String.format("Enter: cleanApplications(%s)", set.toString()));
		cleanRecords(Constants.TYPE_APPLICATION, set);
	}

	/**
	 * Remove entries from the database for applications that no longer exist in
	 * IdentityIQ.
	 * 
	 * @param set
	 * @throws SQLException
	 * @throws GeneralException
	 */
	private void cleanIntegrations(Set<String> set) throws GeneralException, SQLException {
		log.debug(String.format("Enter: cleanIntegrations(%s)", set.toString()));
		cleanRecords(Constants.TYPE_INTEGRATION, set);
	}

	/**
	 * 
	 * @param objectType
	 * @param set
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void cleanRecords(String objectType, Set<String> set) throws GeneralException, SQLException {
		log.debug(String.format("Enter: cleanRecords(%s, %s)", objectType, set.toString()));
		Connection connection = getDatabaseConnection();
		PreparedStatement statement = null;
		ResultSet result = null;
		try {
			statement = connection.prepareStatement("SELECT name FROM " + Constants.TABLE_NAME + " WHERE objecttype = ?");
			statement.setString(1, objectType);
			result = statement.executeQuery();
			Set<String> dbSet = new HashSet<String>();
			while (result.next()) {
				dbSet.add(result.getString("name"));
			}
			Iterator<String> iterator = set.iterator();
			while (iterator.hasNext()) {
				String name = iterator.next();
				if (dbSet.contains(name)) {
					dbSet.remove(name);
				}
			}
			if (!dbSet.isEmpty()) {
				for (String name : dbSet) {
					log.info(String.format("Deleting record for %s named %s", objectType, name));
					PreparedStatement ds = null;
					try {
						ds = connection.prepareStatement("DELETE FROM " + Constants.TABLE_NAME + " WHERE objecttype = ? AND name = ?");
						ds.setString(1, objectType);
						ds.setString(2, name);
						ds.execute();
					} finally {
						if (ds != null) {
							ds.close();
						}
					}
				}
			}
		} finally {
			if (result != null) {
				result.close();
			}
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
	}

	/**
	 * 
	 * @param name
	 * @return
	 * @throws SQLException
	 * @throws GeneralException
	 */
	private boolean integrationExists(String name) throws SQLException, GeneralException {
		log.debug(String.format("Enter: integrationExists(%s)", name));
		return recordExists(Constants.TYPE_INTEGRATION, name);
	}

	/**
	 * 
	 * @param name
	 * @return
	 * @throws SQLException
	 * @throws GeneralException
	 */
	private boolean applicationExists(String name) throws SQLException, GeneralException {
		log.debug(String.format("Enter: applicationExists(%s)", name));
		return recordExists(Constants.TYPE_APPLICATION, name);
	}

	/**
	 * 
	 * @param objectType
	 * @param name
	 * @return
	 * @throws SQLException
	 * @throws GeneralException
	 */
	private boolean recordExists(String objectType, String name) throws SQLException, GeneralException {
		log.debug(String.format("Enter: recordExists(%s, %s)", objectType, name));
		Connection connection = getDatabaseConnection();
		PreparedStatement statement = null;
		try {
			statement = connection.prepareStatement("SELECT count(*) FROM " + Constants.TABLE_NAME + " WHERE objecttype = ? AND name = ?");
			statement.setString(1, objectType);
			statement.setString(2, name);
			ResultSet result = statement.executeQuery();
			if (result.next()) {
				return (result.getInt(1) > 0);
			}
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
		return false;
	}

	/**
	 * 
	 * @param objectType
	 * @param name
	 * @return
	 * @throws SQLException
	 * @throws GeneralException
	 */
	private String getCurrentStatus(String objectType, String name) throws SQLException, GeneralException {
		log.debug(String.format("Enter: getCurrentStatus(%s, %s)", objectType, name));
		Connection connection = getDatabaseConnection();
		PreparedStatement statement = null;
		try {
			statement = connection.prepareStatement("SELECT status FROM " + Constants.TABLE_NAME + " WHERE objecttype = ? AND name = ?");
			statement.setString(1, objectType);
			statement.setString(2, name);
			ResultSet result = statement.executeQuery();
			if (result.next()) {
				return result.getString("status");
			}
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
		return Constants.STATUS_UNKNOWN;
	}
	
	/**
	 * 
	 * @param name
	 * @param id
	 * @param status
	 * @param message
	 * @param timestamp
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void insertIntegrationStatus(String name, String id, String status, String message, Date timestamp) throws GeneralException, SQLException {
		insertStatusRecord(Constants.TYPE_INTEGRATION, name, id, status, message, timestamp);
	}

	/**
	 * 
	 * @param name
	 * @param id
	 * @param status
	 * @param message
	 * @param timestamp
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void insertApplicationStatus(String name, String id, String status, String message, Date timestamp) throws GeneralException, SQLException {
		insertStatusRecord(Constants.TYPE_APPLICATION, name, id, status, message, timestamp);
	}

	/**
	 * 
	 * @param objectType
	 * @param name
	 * @param id
	 * @param status
	 * @param message
	 * @param timestamp
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void insertStatusRecord(String objectType, String name, String id, String status, String message, Date timestamp) throws GeneralException, SQLException {
		Connection connection = getDatabaseConnection();
		PreparedStatement statement = null;
		String sql = "INSERT INTO " + Constants.TABLE_NAME + " (";
		sql += "id, name, objecttype, status, lastcheck, message";
		sql += " ) VALUES (";
		sql += "?, ?, ?, ?, ?, ?";
		sql += ")";
		log.debug("SQL Query: " + sql);
		try {
			statement = connection.prepareStatement(sql);
			int pi = 1;
			statement.setString(pi++, id);
			statement.setString(pi++, name);
			statement.setString(pi++, objectType);
			statement.setString(pi++, status);
			if (timestamp != null) {
				statement.setTimestamp(pi++, new java.sql.Timestamp(timestamp.getTime()));
			} else {
				statement.setTimestamp(pi++, null);
			}
			statement.setString(pi++, message);
			statement.execute();
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
	}

	/**
	 * 
	 * @param name
	 * @param id
	 * @param status
	 * @param message
	 * @param timestamp
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void updateIntegrationStatus(String name, String id, String status, String message, Date timestamp) throws GeneralException, SQLException {
		updateStatusRecord(Constants.TYPE_INTEGRATION, name, id, status, message, timestamp);
	}

	/**
	 * 
	 * @param name
	 * @param id
	 * @param status
	 * @param message
	 * @param timestamp
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void updateApplicationStatus(String name, String id, String status, String message, Date timestamp) throws GeneralException, SQLException {
		updateStatusRecord(Constants.TYPE_APPLICATION, name, id, status, message, timestamp);
	}

	/**
	 * 
	 * @param objectType
	 * @param name
	 * @param id
	 * @param status
	 * @param message
	 * @param timestamp
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void updateStatusRecord(String objectType, String name, String id, String status, String message, Date timestamp) throws GeneralException, SQLException {
		Connection connection = getDatabaseConnection();
		PreparedStatement statement = null;
		String sql = "UPDATE " + Constants.TABLE_NAME + " SET ";
		sql += "objecttype = ?";
		sql += ", id = ?";
		sql += ", status = ?";
		sql += ", message = ?";
		if (timestamp != null) {
			sql += ", lastcheck = ?";
		}
		sql += " WHERE name = ?";
		log.debug("SQL Query: " + sql);
		try {
			statement = connection.prepareStatement(sql);
			int pi = 1;
			statement.setString(pi++, objectType);
			statement.setString(pi++, id);
			statement.setString(pi++, status);
			statement.setString(pi++, message);
			if (timestamp != null) {
				statement.setTimestamp(pi++, new java.sql.Timestamp(timestamp.getTime()));
			}
			statement.setString(pi, name);
			statement.execute();
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
	}

	/**
	 * 
	 * @param name
	 * @param id
	 * @param status
	 * @param message
	 * @param timestamp
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void setIntegrationStatus(String name, String id, String status, String message, Date timestamp) throws GeneralException, SQLException {
		log.debug(String.format("Enter: setIntegrationStatus(%s, %s, %s, %s, %s)", name, id, status, message, (timestamp != null) ? timestamp.toString() : "null"));
		String oldStatus = getCurrentStatus(Constants.TYPE_INTEGRATION, name);
		if (!Util.nullSafeEq(oldStatus, status)) {
			statusChangeAction(Constants.TYPE_INTEGRATION, name, oldStatus, status);
		}
		if (integrationExists(name)) {
			updateIntegrationStatus(name, id, status, message, timestamp);
		} else {
			insertIntegrationStatus(name, id, status, message, timestamp);
		}
	}

	/**
	 * 
	 * @param name
	 * @param id
	 * @param status
	 * @param message
	 * @param timestamp
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void setApplicationStatus(String name, String id, String status, String message, Date timestamp) throws GeneralException, SQLException {
		log.debug(String.format("Enter: setApplicationStatus(%s, %s, %s, %s, %s)", name, id, status, message, (timestamp != null) ? timestamp.toString() : "null"));
		String oldStatus = getCurrentStatus(Constants.TYPE_APPLICATION, name);
		if (!Util.nullSafeEq(oldStatus, status)) {
			statusChangeAction(Constants.TYPE_APPLICATION, name, oldStatus, status);
		}
		if (applicationExists(name)) {
			updateApplicationStatus(name, id, status, message, timestamp);
		} else {
			insertApplicationStatus(name, id, status, message, timestamp);
		}
	}

	/**
	 * 
	 * @param context
	 * @param name
	 * @param id
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void testIntegration(SailPointContext context, String name, String id) throws GeneralException, SQLException {
		log.debug(String.format("Enter: testIntegration(%s, %s, %s)", context.toString(), name, id));
		IntegrationConfig integration = context.getObjectByName(IntegrationConfig.class, name);
		if (integration != null) {
			String executorName = integration.getExecutor();
			try {
				if (executorName != null) {
					Class<?> connClass = Class.forName(executorName);
					Constructor<?> constructor = connClass.getConstructor();
					IntegrationInterface executor = (IntegrationInterface) constructor.newInstance();
					Attributes<String, Object> attributes = integration.getAttributes();
					executor.configure(attributes);
					executor.ping();
				}
			} catch (Exception e) {
				String message = e.getMessage();
				if (message == null) {
					message = "Ping failed";
				}
				log.error(message);
				setIntegrationStatus(name, id, Constants.STATUS_ERROR, message, new Date());
				return;
			}
			setIntegrationStatus(name, id, Constants.STATUS_OK, null, new Date());
		} else {
			setIntegrationStatus(name, id, Constants.STATUS_ERROR, "IntegrationConfig not found", null);
		}
	}

	/**
	 * 
	 * @param context
	 * @param name
	 * @param id
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private void testApplication(SailPointContext context, String name, String id) throws GeneralException, SQLException {
		log.debug(String.format("Enter: testApplication(%s, %s, %s)", context.toString(), name, id));
		Application application = context.getObjectByName(Application.class, name);
		if (application != null) {
			String connectorName = application.getConnector();
			try {
				Class<?> connClass = Class.forName(connectorName);
				Class<?>[] paramTypes = { Application.class };
				Constructor<?> constructor = connClass.getDeclaredConstructor(paramTypes);
				Object[] params = { application };
				AbstractConnector connector = (AbstractConnector) constructor.newInstance(params);
				connector.testConfiguration();
			} catch (Exception e) {
				log.error(e.getMessage());
				setApplicationStatus(name, id, Constants.STATUS_ERROR, e.getMessage(), new Date());
				return;
			}
			setApplicationStatus(name, id, Constants.STATUS_OK, null, new Date());
		} else {
			setApplicationStatus(name, id, Constants.STATUS_ERROR, "Application not found", null);
		}
	}

	@Override
	public void execute(SailPointContext context) throws GeneralException {
		log.debug(String.format("Enter: execute(%s)", context.toString()));
		Map<String, String> applications = getIIQApplications(context);
		Map<String, String> integrations = getIIQIntegrations(context);

		if (applications != null) {
			Set<String> names = applications.keySet();
			for (String name : names) {
				log.trace(String.format("Testing application", name));
				String id = applications.get(name);
				try {
					testApplication(context, name, id);
				} catch (GeneralException e) {
					log.error(e.getMessage());
					e.printStackTrace();
				} catch (SQLException e) {
					log.error(e.getMessage());
					e.printStackTrace();
				}
			}
		}
		try {
			cleanApplications(applications.keySet());
		} catch (SQLException e) {
			log.error(e.getMessage());
			e.printStackTrace();
		}

		if (integrations != null) {
			Set<String> names = integrations.keySet();
			for (String name : names) {
				log.trace(String.format("Testing integration", name));
				String id = integrations.get(name);
				try {
					testIntegration(context, name, id);
				} catch (GeneralException e) {
					log.error(e.getMessage());
					e.printStackTrace();
				} catch (SQLException e) {
					log.error(e.getMessage());
					e.printStackTrace();
				}
			}
		}
		try {
			cleanIntegrations(integrations.keySet());
		} catch (SQLException e) {
			log.error(e.getMessage());
			e.printStackTrace();
		}
	}
}
