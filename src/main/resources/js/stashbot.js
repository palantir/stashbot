
require(['aui/form-notification']);
require(['aui/form-validation']);
require(['jquery'], function($) {
	console.debug("Injecting JS to disable dropdown")
	$(window).load(function() {

		// Activate tooltip on any item with this class
		AJS.$(".tooltip-stashbot-class").tooltip();

		console.debug("Detecting if jenkins server config is locked or not")
		locked = $("#isJenkinsServerLocked")
		if (locked.text() == "locked") {
			console.debug("Locking dropdown")
			dd = $("#jenkinsServerName")
			// FINALLY figured this out via http://stackoverflow.com/questions/10570070/how-to-disable-enable-select-field-using-jquery
			dd.prop('disabled', 'disabled')
			dd.parent().append("<font color=\"red\">Cannot change Jenkin Server - LOCKED to admins only</font>")
		}

    /*
      The map of what checkbox disables which fields/checkboxes.
         ID_OF_CHECKBOX => [ ID_OF_FORM_ELEMENT_1, ID_OF_FORM_ELEMENT_2, ... ]
    */
    var disableMap = {
      "isPublishPinned" : [ "publishLabel" ],
      "isVerifyPinned" : [ "verifyLabel" ],
      "isJunit" : [ "junitPath" ],
      "isEmailNotificationsEnabled" : [ "emailRecipients", "isEmailForEveryUnstableBuild",
                                        "isEmailSendToIndividuals", "isEmailPerModuleEmail" ]
    };

    function enableItem ( item ) {
      item.removeClass("palantir-stashbot-disabled");
      if (item.prop("type") == "checkbox") {
        item.siblings().addBack().unwrap();
        item.unbind("change", checkboxDisabler);
      } else {
        item.prop("readonly", false);
      }
    }

    function disableItem ( item ) {
      item.addClass("palantir-stashbot-disabled");
      if (item.prop("type") == "checkbox") {
        item.siblings().addBack().wrapAll("<span class='palantir-stashbot-disabled'></span>");
        item.bind("change", checkboxDisabler);
      } else {
        item.prop("readonly", true);
      }
    }

    var checkboxDisabler = function () {
      $(this).prop("checked", !$(this).prop("checked"));
    };

    $.each(disableMap, function ( enabler, enablees ) {
      if ($("#" + enabler).is(":checked")) {
        $.each ( enablees, function (i) {
          enableItem($("#" + enablees[i]));
        });
      } else {
        $.each ( enablees, function (i) {
          disableItem($("#" + enablees[i]));
        });
      }

      $("#" + enabler).change(function () {
        $.each ( disableMap[enabler], function (i) {
          var enablee = $("#" + enablees[i]);
          if ( $("#" + enabler).is(":checked")) {
            enableItem(enablee);
          } else {
            disableItem(enablee);
          }
        });
      });
    });


	})
})
