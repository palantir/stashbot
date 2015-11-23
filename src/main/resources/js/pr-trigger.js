// Look at /stash/plugins/servlet/stashbot/repo-status/$PROJECT/$REPO to figure out whether to display the link or not
jQuery(document).ready(function () {
        console.debug("Loading Stashbot Trigger Code\n\n");
        require(['bitbucket/util/state', 'bitbucket/util/navbuilder', 'jquery'], function(state, nav, $) {
                // TODO: if this ever breaks, see if these paths have changed...
                // xpath = //*[@id="aui-page-panel-content-body"]/div/section/div/div/div[1]/div[2]
                // css path = #aui-page-panel-content-body > div > section > div > div > div.aui-group > div.aui-item.summary-panel
                var pr_summary_panel = document.getElementsByClassName('summary-panel');
                add_inner_html = function(elem, html) {
                    elem.innerHTML = elem.innerHTML + html;
                }
                var base_url = nav.pluginServlets().build();
                console.debug("Base URL: " + base_url);
                var pr = state.getPullRequest();
                var pr_id = pr.id;
                var repo_id = pr.toRef.repository.id;
                var from = pr.fromRef.latestCommit;
                var to = pr.toRef.latestCommit;
                var trigger_url = base_url + '/stashbot/build-trigger/' + repo_id + '/verify_pr/' + from + '/' + to + '/' + pr_id;
                var url_split = document.URL.split("/");
                var project = url_split[url_split.indexOf("projects") + 1];
                var repo = url_split[url_split.indexOf("repos") + 1];
                var status_url = base_url + '/stashbot/repo-status/' + project + '/' + repo;
                console.debug("PR Trigger Link URL: " + trigger_url);
                console.debug("Status URL: " + status_url);
                jQuery.get(status_url).done(function(data) {
                  if (data.includes("true")) {
                    add_inner_html(pr_summary_panel[0], '<a href="' + trigger_url + '">Trigger PR Build</a>');
                  }
                });
        });
});
