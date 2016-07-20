/*

Other available values to build paths, etc. that are obtained from the server and set in the PluginFramework JS object

PluginFramework.PluginBaseEndpointName = '#{pluginFramework.basePluginEndpointName}';
PluginFramework.PluginEndpointRoot = '#{pluginFramework.basePluginEndpointName}/#{pluginFramework.basePluginEndpointName}';
PluginFramework.PluginFolderName = '#{pluginFramework.pluginFolderName}';
PluginFramework.CurrentPluginUniqueName = '#{pluginFramework.uniqueName}';
PluginFramework.CsrfToken = Ext.util.Cookies.get('CSRF-TOKEN');

 */


var appStatusUrl = SailPoint.CONTEXT_PATH + '/pluginPage.jsf?pn=applicationhealthplugin';
var jQueryClone = jQuery;
jQuery(document).ready(function(){

	jQuery("ul.navbar-right li:first")
		.before(
				'<li class="dropdown">' +
				'		<a href="' + appStatusUrl + '" tabindex="0" role="menuitem" data-snippet-debug="off">' +
				'			<i id="appHealthStatusIcon" role="presenation" class="fa fa-refresh fa-spin fa-lg healthUNKNOWN"></i>' +
				'		</a>' +
				'</li>'
		);
	
    jQueryClone.ajax({
        method: "GET",
        beforeSend: function (request) {
            request.setRequestHeader("X-XSRF-TOKEN", PluginFramework.CsrfToken);
        },
        url: SailPoint.CONTEXT_PATH + "/plugin/applicationhealthplugin/getStatus"
    })
    .done(function (msg) {
        healthstatus = msg._status;
        statusClass = 'health' + healthstatus;
	    document.getElementById("appHealthStatusIcon").className = 'fa fa-refresh fa-spin fa-lg ' + statusClass;
    });

	setInterval(function(){
	    jQueryClone.ajax({
	        method: "GET",
	        beforeSend: function (request) {
	            request.setRequestHeader("X-XSRF-TOKEN", PluginFramework.CsrfToken);
	        },
	        url: SailPoint.CONTEXT_PATH + "/plugin/applicationhealthplugin/getStatus"
	    })
	    .done(function (msg) {
	        healthstatus = msg._status;
	        statusClass = 'health' + healthstatus;
		    document.getElementById("appHealthStatusIcon").className = 'fa fa-refresh fa-spin fa-lg ' + statusClass;
	    });
	
	
    }, 15000);

	
});
