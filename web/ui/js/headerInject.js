//<script src="ui/js/jquery.min.js"></script>

/*

Other available values to build paths, etc. that are obtained from the server and set in the PluginFramework JS object

PluginFramework.PluginBaseEndpointName = '#{pluginFramework.basePluginEndpointName}';
PluginFramework.PluginEndpointRoot = '#{pluginFramework.basePluginEndpointName}/#{pluginFramework.basePluginEndpointName}';
PluginFramework.PluginFolderName = '#{pluginFramework.pluginFolderName}';
PluginFramework.CurrentPluginUniqueName = '#{pluginFramework.uniqueName}';
PluginFramework.CsrfToken = Ext.util.Cookies.get('CSRF-TOKEN');

 */


var hostsUrl = SailPoint.CONTEXT_PATH + '/systemSetup/hostConfig.jsf?forceLoad=true';
var jQueryClone = jQuery;
jQuery(document).ready(function(){

	jQuery("ul.navbar-right li:first")
		.before(
				'<li class="dropdown">' +
				'		<a href="' + hostsUrl + '" tabindex="0" role="menuitem" data-snippet-debug="off">' +
				'			<i id="systemHealthStatusIcon" role="presenation" class="fa fa-heart fa-lg healthUNKNOWN"></i>' +
				'		</a>' +
				'</li>'
		);
	
	setInterval(function(){
	    jQueryClone.ajax({
	        method: "GET",
	        beforeSend: function (request) {
	            request.setRequestHeader("X-XSRF-TOKEN", PluginFramework.CsrfToken);
	        },
	        url: SailPoint.CONTEXT_PATH + "/plugin/systemhealthplugin/getStatus"
	    })
	    .done(function (msg) {
	        healthstatus = msg._status;
	        statusClass = 'health' + healthstatus;
		    document.getElementById("systemHealthStatusIcon").className = 'fa fa-heart fa-lg ' + statusClass;
	    });
	
	
    }, 15000);

	
});
