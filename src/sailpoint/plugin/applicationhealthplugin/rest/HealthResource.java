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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Custom;
import sailpoint.plugin.applicationhealthplugin.ApplicationHealthDTO;
import sailpoint.plugin.applicationhealthplugin.Constants;
import sailpoint.plugin.rest.AbstractPluginRestResource;
import sailpoint.plugin.rest.jaxrs.SPRightsRequired;
import sailpoint.plugin.server.PluginEnvironment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

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

	private List<?> getIgnoredList(String objectType) {
		Custom custom = getCustomConfig();
		String entryName = String.format("ignored%ss", objectType);
		List<?> list = custom.getList(entryName);
		if (list == null) {
			list = new ArrayList<String>();
		}
		return list;
	}
	
	private List<String[]> getIgnoredItems() {
		List<String[]> list = new ArrayList<String[]>();
		for (Object name: getIgnoredList(Constants.TYPE_APPLICATION)) {
			String[] item = new String[2];
			item[0] = Constants.TYPE_APPLICATION;
			item[1] = (String) name;
			list.add(item);
		}
		for (Object name: getIgnoredList(Constants.TYPE_INTEGRATION)) {
			String[] item = new String[2];
			item[0] = Constants.TYPE_INTEGRATION;
			item[1] = (String) name;
			list.add(item);
		}
		return list;
	};

	/**
	 * Count the number of applications and integrations that are not OK.
	 * 
	 * @return
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private int countIssues() throws GeneralException, SQLException {
		List<Map<String, String>> issueList = getIssueList();
		return (issueList == null)?0:issueList.size();
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
			return new ArrayList<Map<String, String>>();
		}
		ResultSet result = null;
		PreparedStatement statement = connection.prepareStatement("SELECT name, id, objecttype, lastcheck, message FROM " + Constants.TABLE_NAME + " WHERE NOT ( status = ? )  ORDER BY name");
		List<Map<String, String>> issueList = new ArrayList<Map<String, String>>();
		try {
			List<?> ignoredApplications = getIgnoredList(Constants.TYPE_APPLICATION);
			List<?> ignoredIntegrations = getIgnoredList(Constants.TYPE_INTEGRATION);
			statement.setString(1, Constants.STATUS_OK);
			result = statement.executeQuery();
			while (result.next()) {
				String name = result.getString("name");
				String id = result.getString("id");
				String objecttype = result.getString("objecttype");
				Date lastcheck = result.getTimestamp("lastcheck");
				String message = result.getString("message");
				if (!((Constants.TYPE_APPLICATION.equals(objecttype) && ignoredApplications.contains(name)) || (Constants.TYPE_INTEGRATION.equals(objecttype) && ignoredIntegrations.contains(name)))) {
					Map<String, String> map = new HashMap<String, String>();
					map.put("name", name);
					map.put("id", id);
					map.put("objecttype", objecttype);
					SimpleDateFormat format = new SimpleDateFormat(Constants.DEFAULT_DATE_FORMAT);
					map.put("lastcheck", format.format(lastcheck));
					map.put("message", message);
					issueList.add(map);
				}
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

	private Custom getCustomConfig() {
		try {
			return (Custom) getContext().getObjectByName(Custom.class, Constants.CUSTOM_CONFIG);
		} catch (GeneralException e) {
			log.error(e);
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Add an application or integration to the ignored list.
	 * 
	 * @param objectType
	 * @param name
	 * @return
	 * @throws GeneralException
	 */
	private boolean markAsIgnored(String objectType, String name) throws GeneralException {
		Custom custom = getCustomConfig();
		SailPointContext context = getContext();
		if (custom != null) {
			if (Constants.TYPE_APPLICATION.equals(objectType) || Constants.TYPE_INTEGRATION.equals(objectType)) {
				String entryName = String.format("ignored%ss", objectType);
				@SuppressWarnings("unchecked")
				List<String> list = custom.getList(entryName);
				if (list == null) {
					list = new ArrayList<String>();
				}
				if (!list.contains(name)) {
					list.add(name);
					custom.put(entryName, list);
					context.saveObject(custom);
					context.commitTransaction();
				}
				return true;
			} else {
				log.error(String.format("Incorrect type %s", objectType));
			}
		}
		return false;
	}

	/**
	 * Remove an application or integration from the ignored list.
	 * 
	 * @param objectType
	 * @param name
	 * @return
	 * @throws GeneralException
	 */
	private boolean unmarkAsIgnored(String objectType, String name) throws GeneralException {
		Custom custom = getCustomConfig();
		SailPointContext context = getContext();
		if (custom != null) {
			if (Constants.TYPE_APPLICATION.equals(objectType) || Constants.TYPE_INTEGRATION.equals(objectType)) {
				String entryName = String.format("ignored%ss", objectType);
				@SuppressWarnings("unchecked")
				List<String> list = custom.getList(entryName);
				if (list == null) {
					list = new ArrayList<String>();
				}
				if (list.contains(name)) {
					list.remove(name);
					custom.put(entryName, list);
					context.saveObject(custom);
					context.commitTransaction();
				}
				return true;
			} else {
				log.error(String.format("Incorrect type %s", objectType));
			}
		}
		return false;
	}

	/**
	 * Produce an HTML table with the issues.
	 * 
	 * @return
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private String getIssueTableHtml() throws GeneralException, SQLException {
		// Get Rows and insert Data
		List<Map<String, String>> issueList = getIssueList();
		if (issueList == null || issueList.isEmpty()) {
			return "<div class=\"allOk\"/>";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<div id=\"bodyDivTitle\"><h1>Issues</h1></div>\n");
		sb.append("<table class='applicationHealthTable'>\n");
		sb.append("<tr class='applicationHealthTable x-grid-header-row'>\n");
		sb.append("<th class='applicationHealthTable'>Type</th><th class='applicationHealthTable'>Name</th><th class='applicationHealthTable'>Last Check</th><th class='applicationHealthTable'>Error</th>\n");
		sb.append("</tr>\n");

		boolean odd = true;
		if (issueList != null && !issueList.isEmpty()) {
			for (Map<String, String> issue : issueList) {
				sb.append("<tr class='applicationHealthTable " + (odd ? "odd" : "even") + "'>\n");
				sb.append("<td class='applicationHealthTable'>" + issue.get("objecttype") + "</td>");
				sb.append("<td class='applicationHealthTable'>");
				String uuid = Util.uuid();
			    sb.append(String.format("&nbsp;<input id=\"%s\" value=\"Ignore\" class=\"secondaryBtn applicationHealthIgnore\" type=\"submit\"/>", uuid));
			    sb.append(String.format("<script type=\"text/javascript\">\n$(\'#%s').click(function(event) {\n  event.preventDefault();\n  submitIgnore(\"%s\", \"%s\");\n});\n</script>", uuid, issue.get("objecttype"), issue.get("name")));
				sb.append("&nbsp;" +  issue.get("name"));
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
	 * Produce an HTML table with the issues.
	 * 
	 * @return
	 * @throws GeneralException
	 * @throws SQLException
	 */
	private String getIgnoredTableHtml() throws GeneralException, SQLException {
		// Get Rows and insert Data
		List<String[]> ignoredList = getIgnoredItems();
		if (ignoredList == null || ignoredList.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<div id=\"bodyDivTitle\"><h1>Ignored Applications and Integrations</h1></div>\n");
		sb.append("<table class='applicationHealthTable'>\n");
		sb.append("<tr class='applicationHealthTable x-grid-header-row'>\n");
		sb.append("<th class='applicationHealthTable'>Type</th><th class='applicationHealthTable'>Name</th>\n");
		sb.append("</tr>\n");

		
		boolean odd = true;
		if (ignoredList != null && !ignoredList.isEmpty()) {
			for (String[] item : ignoredList) {
				sb.append("<tr class='applicationHealthTable " + (odd ? "odd" : "even") + "'>\n");
				sb.append("<td class='applicationHealthTable'>" + item[0] + "</td>");
				sb.append("<td class='applicationHealthTable'>");
				String uuid = Util.uuid();
			    sb.append(String.format("<input id=\"%s\" value=\"Restore\" class=\"secondaryBtn applicationHealthIgnore\" type=\"submit\"/>", uuid));
			    sb.append(String.format("<script type=\"text/javascript\">\n$(\'#%s').click(function(event) {\n  event.preventDefault();\n  submitUnignore(\"%s\", \"%s\");\n});\n</script>", uuid, item[0], item[1]));
			    sb.append("&nbsp;" + item[1]);
				sb.append("</td>");
				sb.append("</tr>\n");
			}
		}

		sb.append("</table>\n");
		return sb.toString();
	}

	/**
	 * Returns the application health status
	 * 
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
	 * 
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
		healthDTO.set_status(getIssueTableHtml());
		return healthDTO;
	}

	/**
	 * Returns an HTML table with the failed applications
	 * 
	 * @return
	 * @throws Exception
	 */
	@SPRightsRequired(value = { "SystemHealthPluginRestServiceAllow" })
	@GET
	@Path("getIgnoredTable")
	@Produces(MediaType.APPLICATION_JSON)
	public ApplicationHealthDTO getIgnoredTable() throws Exception {
		log.debug("Enter: getIgnoredTable()");
		ApplicationHealthDTO healthDTO = new ApplicationHealthDTO();
		healthDTO.set_status(getIgnoredTableHtml());
		return healthDTO;
	}

	/**
	 * 
	 * @param objectType
	 * @param name
	 * @return
	 * @throws Exception
	 */
	@SPRightsRequired(value = { "SystemHealthPluginRestServiceAllow" })
	@GET
	@Path("addIgnored/{objectType}/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	public ApplicationHealthDTO addIgnored(@PathParam("objectType") String objectType, @PathParam("name") String name) throws Exception {
		boolean success = markAsIgnored(objectType, name);
		ApplicationHealthDTO healthDTO = new ApplicationHealthDTO();
		healthDTO.set_status(success ? "OK" : "Error");
		return healthDTO;
	}

	/**
	 * 
	 * @param objectType
	 * @param name
	 * @return
	 * @throws Exception
	 */
	@SPRightsRequired(value = { "SystemHealthPluginRestServiceAllow" })
	@GET
	@Path("removeIgnored/{objectType}/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	public ApplicationHealthDTO removeIgnored(@PathParam("objectType") String objectType, @PathParam("name") String name) throws Exception {
		boolean success = unmarkAsIgnored(objectType, name);
		ApplicationHealthDTO healthDTO = new ApplicationHealthDTO();
		healthDTO.set_status("OK");
		healthDTO.set_status(success ? "OK" : "Error");
		return healthDTO;
	}

}
