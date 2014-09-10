
require(['jquery'], function($) {
	console.debug("Injecting JS to disable dropdown")
	$(window).load(function() {
		console.debug("Detecting if jenkins server config is locked or not")
		locked = $("#isJenkinsServerLocked")
		if (locked.text() == "locked") {
			console.debug("Locking dropdown")
			dd = $("#jenkinsServerName")
			// FINALLY figured this out via http://stackoverflow.com/questions/10570070/how-to-disable-enable-select-field-using-jquery
			dd.prop('disabled', 'disabled')
			dd.parent().append("<font color=\"red\">Cannot change Jenkin Server - LOCKED to admins only</font>")
		}
	})
})