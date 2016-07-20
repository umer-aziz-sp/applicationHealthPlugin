package sailpoint.plugin.applicationhealthplugin.rest;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.plugin.applicationhealthplugin.ApplicationHealthDTO;
import sailpoint.plugin.applicationhealthplugin.Constants;
import sailpoint.plugin.rest.AbstractPluginRestResource;
import sailpoint.plugin.rest.jaxrs.SPRightsRequired;
import sailpoint.plugin.server.PluginEnvironment;
import sailpoint.tools.GeneralException;

//import sailpoint.plugin.rest.jaxrs.AllowAll;

/**
 * @author Menno Pieters
 * 
 */

@SPRightsRequired(value = { "ApplicationHealthPluginRestServiceAllow" })
@Path("applicationhealthplugin")
public class HealthResource extends AbstractPluginRestResource {

	private static Log log = LogFactory.getLog(HealthResource.class);

	/**
	 * Constructor
	 * 
	 */
	public HealthResource() {
		log.info("Constructor: HealthResource");
	}

	/**
	 * Get plugin database connection.
	 * 
	 * @return
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private Connection getDatabaseConnection() throws GeneralException, SQLException {
		PluginEnvironment environment = PluginEnvironment.getEnvironment();
		return environment.getJDBCConnection();
	}

	/**
	 * Count the number of applications and integrations that are not OK.
	 * 
	 * @return
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private int countIssues() throws GeneralException, SQLException {
		Connection connection = getDatabaseConnection();
		if (connection == null) {
			return -1;
		}
		int issues = 0;
		ResultSet result = null;
		PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + Constants.TABLE_NAME + " WHERE NOT ( status = ? )");
		try {
			statement.setString(1, Constants.STATUS_OK);
			result = statement.executeQuery();
			if (result.next()) {
				issues = result.getInt(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
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
		return issues;
	}

	/**
	 * Retrieve all server hosts and analyze their status.
	 * 
	 * @return
	 * @throws SQLException
	 * @throws GeneralException
	 */
	private String getOverallApplicationStatus() throws GeneralException, SQLException {
		String status = Constants.STATUS_UNKNOWN;
		int issues = countIssues();
		if (issues == 0) {
			status = Constants.STATUS_OK;
		} else if (issues > 0) {
			status = Constants.STATUS_ERROR;
		}
		return status;
	}
	
	/**
	 * Get the list of issues reported by the system.
	 * 
	 * @return
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private List<Map<String, String>> getIssueList() throws GeneralException, SQLException {
		Connection connection = getDatabaseConnection();
		if (connection == null) {
			return new ArrayList<Map<String,String>>();
		}
		ResultSet result = null;
		PreparedStatement statement = connection.prepareStatement("SELECT name, id, objecttype, lastcheck, message FROM " + Constants.TABLE_NAME + " WHERE NOT ( status = ? )  ORDER BY name");
		List<Map<String, String>> issueList = new ArrayList<Map<String, String>>();
		try {
			statement.setString(1, Constants.STATUS_OK);
			result = statement.executeQuery();
			while (result.next()) {
				String name = result.getString("name");
				String id = result.getString("id");
				String objecttype = result.getString("objecttype");
				Date lastcheck = result.getTimestamp("lastcheck");
				String message = result.getString("message");
				Map<String, String> map = new HashMap<String, String>();
				map.put("name", name);
				map.put("id", id);
				map.put("objecttype", objecttype);
				SimpleDateFormat format = new SimpleDateFormat(Constants.DEFAULT_DATE_FORMAT);
				map.put("lastcheck", format.format(lastcheck));
				map.put("message", message);
				issueList.add(map);
			}
		} catch (SQLException e) {
			e.printStackTrace();
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
		return issueList;
		
	}
	
	/**
	 * Return the base url of the plugin container.
	 * 
	 * @return
	 */
	protected String getPluginBaseUrl() {
		URI uri = this.uriInfo.getBaseUri();
		return uri.toString();
	}
	
	/**
	 * Produce an HTML table with the issues.
	 * 
	 * @return
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private String getIssueTable() throws GeneralException, SQLException {
		// Get Rows and insert Data
		List<Map<String, String>> issueList = getIssueList();
		if (issueList == null || issueList.isEmpty()) {
			return "<div class=\"allOk\"/>";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<table class='applicationHealthTable'>\n");
		sb.append("<tr class='applicationHealthTable x-grid-header-row'>\n");
		sb.append("<th class='applicationHealthTable'>Type</th><th class='applicationHealthTable'>Name</th><th class='applicationHealthTable'>Last Check</th><th class='applicationHealthTable'>Error</th>\n");
		sb.append("</tr>\n");
		
		boolean odd = true;
		if (issueList != null && !issueList.isEmpty()) {
			for (Map<String, String> issue: issueList) {
				sb.append("<tr class='applicationHealthTable " + (odd?"odd":"even") + "'>\n");
				sb.append("<td class='applicationHealthTable'>" + issue.get("objecttype") + "</td>");
				sb.append("<td class='applicationHealthTable'>" + issue.get("name"));
				if (Constants.TYPE_APPLICATION.equals(issue.get("objecttype"))) {
					
					sb.append(String.format("<a href=\"%s../define/applications/application.jsf?appId=%s\"><i class=\"fa fa-cog\"/></a>", getPluginBaseUrl(), issue.get("id")));
				}
				sb.append("</td>");
				sb.append("<td class='applicationHealthTable'>" + issue.get("lastcheck") + "</td>");
				sb.append("<td class='applicationHealthTable'>" + issue.get("message") + "</td>");
				sb.append("</tr>\n");				
			}
		}
		
		sb.append("</table>\n");
		return sb.toString();
	}

	/**
	 * Returns the application health status
	 * @return
	 * @throws Exception
	 */
	@SPRightsRequired(value = { "SystemHealthPluginRestServiceAllow" })
	@GET
	@Path("getStatus")
	@Produces(MediaType.APPLICATION_JSON)
	public ApplicationHealthDTO getStatus() throws Exception {
		log.debug("Enter: getStatus()");
		ApplicationHealthDTO healthDTO = new ApplicationHealthDTO();
		healthDTO.set_status(getOverallApplicationStatus());
		return healthDTO;
	}
	
	/**
	 * Returns an HTML table with the failed applications
	 * @return
	 * @throws Exception
	 */
	@SPRightsRequired(value = { "SystemHealthPluginRestServiceAllow" })
	@GET
	@Path("getStatusTable")
	@Produces(MediaType.APPLICATION_JSON)
	public ApplicationHealthDTO getStatusTable() throws Exception {
		log.debug("Enter: getStatusTable()");
		ApplicationHealthDTO healthDTO = new ApplicationHealthDTO();
		healthDTO.set_status(getIssueTable());
		return healthDTO;
	}
	
}
