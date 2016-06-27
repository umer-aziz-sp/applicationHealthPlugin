package sailpoint.plugin.applicationhealthplugin.rest;

import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Server;
import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;
import sailpoint.plugin.rest.AbstractPluginRestResource;
import sailpoint.plugin.rest.jaxrs.SPRightsRequired;
import sailpoint.plugin.systemhealthplugin.SystemHealthDTO;
//import sailpoint.plugin.rest.jaxrs.AllowAll;
import sailpoint.web.plugin.config.Plugin;


/**
 * @author nick.wellinghoff
 */


@SPRightsRequired(value={"ApplicationHealthPluginRestServiceAllow"})
@Path("applicationhealthplugin")
public class HealthResource extends AbstractPluginRestResource {
	
	private static Log log = LogFactory.getLog(HealthResource.class);
	
    public HealthResource() {
    	log.info("Constructor: HealthResource");
    }

    /**
     * Get all server hosts in the IdentityIQ installation.
     * 
     * @return
     */
	private List<Server> getHosts() {
		log.debug("Enter: getHosts()");
		SailPointContext context = getContext();
		if (context != null) {
			try {
				return context.getObjects(Server.class);
			} catch (GeneralException e) {
				log.error(e.getMessage());
				e.printStackTrace();
			}
		}
		log.error("Returning an empty list.");
		return new ArrayList<Server>();
	}
	
	/**
	 * Retrieve all server hosts and analyze their status.
	 * 
	 * @return
	 */
	private String getOverallSystemStatus() {
		List<Server> hosts = getHosts();
		String status = "UNKNOWN";
		if (hosts.isEmpty()) {
			log.error("Empty host list - should not happen!");
			status = "ERROR";
		} else {
			status = "OK";
			for (Server host: hosts) {
				if (host.isInactive()) {
					log.error("Inactive host");
					status = "ERROR";
					break;
				}
				Attributes<String, Object> attributes = host.getAttributes();
				if (attributes.getFloat("cpuUsage") > 80.0) {
					log.warn("CPU load greater than 80%");
					status = "WARN";
				}
			}
		}
		return status;
	}
    
    /**
     * Returns the system health status
     */
    @SPRightsRequired(value={"SystemHealthPluginRestServiceAllow"})
    @GET
    @Path("getStatus")
    @Produces(MediaType.APPLICATION_JSON)

    public SystemHealthDTO
    getStatus() throws Exception {
    	log.debug("Enter: getStatus()");
        SystemHealthDTO healthDTO = new SystemHealthDTO();
        healthDTO.set_status(getOverallSystemStatus());
        return healthDTO;
    }
}
