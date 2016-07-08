package sailpoint.plugin.applicationhealthplugin.rest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.plugin.applicationhealthplugin.ApplicationHealthDTO;
import sailpoint.plugin.rest.AbstractPluginRestResource;
import sailpoint.plugin.rest.jaxrs.SPRightsRequired;
import sailpoint.plugin.server.PluginEnvironment;
import sailpoint.tools.GeneralException;
//import sailpoint.plugin.rest.jaxrs.AllowAll;


/**
 * @author Menno Pieters
 * 
 */


@SPRightsRequired(value={"ApplicationHealthPluginRestServiceAllow"})
@Path("applicationhealthplugin")
public class HealthResource extends AbstractPluginRestResource {
	
	public final static String STATUS_OK = "OK";
	public final static String STATUS_WARN = "WARN";
	public final static String STATUS_ERROR = "ERROR";
	public final static String STATUS_UNKNOWN = "UNKNOWN";
	
	private final static String TABLE_NAME="app_health_data";
	
	private static Log log = LogFactory.getLog(HealthResource.class);
		
	/**
	 * Constructor
	 * 
	 */
    public HealthResource() {
    	log.info("Constructor: HealthResource");
    }
    
    private Connection getDatabaseConnection() throws GeneralException, SQLException {
    	PluginEnvironment environment = PluginEnvironment.getEnvironment();
    	return environment.getJDBCConnection();
    }

    private int countIssues() throws GeneralException, SQLException {
    	Connection connection = getDatabaseConnection();
    	if (connection == null) {
    		return -1;
    	}
    	PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE NOT ( status = ? )");
    	statement.setString(1, STATUS_OK);
    	ResultSet result = statement.executeQuery();
    	if (result.next()) {
    		return result.getInt(1);
    	}
    	return 0;
    }
	
	/**
	 * Retrieve all server hosts and analyze their status.
	 * 
	 * @return
	 * @throws SQLException 
	 * @throws GeneralException 
	 */
	private String getOverallApplicationStatus() throws GeneralException, SQLException {
		String status = STATUS_UNKNOWN;
		int issues = countIssues();
		if (issues == 0) {
			status = STATUS_OK;
		} else if (issues > 0) {
			status = STATUS_ERROR;
		}
		return status;
	}
    
    /**
     * Returns the application health status
     */
    @SPRightsRequired(value={"SystemHealthPluginRestServiceAllow"})
    @GET
    @Path("getStatus")
    @Produces(MediaType.APPLICATION_JSON)
    public ApplicationHealthDTO getStatus() throws Exception {
    	log.debug("Enter: getStatus()");
    	ApplicationHealthDTO healthDTO = new ApplicationHealthDTO();
        healthDTO.set_status(getOverallApplicationStatus());
        return healthDTO;
    }
}
