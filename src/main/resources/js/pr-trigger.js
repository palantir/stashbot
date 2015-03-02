jQuery(document).ready(function () {
        console.debug("Loading Stashbot Trigger Code\n\n")
        require(['model/page-state', 'util/navbuilder', 'jquery'], function(state, nav, $) {

                // TODO: if this ever breaks, see if these paths have changed...
                // xpath = //*[@id="aui-page-panel-content-body"]/div/section/div/div/div[1]/div[2]
                // css path = #aui-page-panel-content-body > div > section > div > div > div.aui-group > div.aui-item.summary-panel
                pr_summary_panel = document.getElementsByClassName('summary-panel')

                add_inner_html = function(elem, html) {
                    elem.innerHTML = elem.innerHTML + html
                }

                base_url = nav.pluginServlets().build()
                pr = state.getPullRequest()
                pr_id = pr.getId()
                repo_id = pr.getToRef().getRepository().getId()
                from = pr.getFromRef().getLatestChangeset()
                to = pr.getToRef().getLatestChangeset()
                trigger_url = base_url + '/stashbot/build-trigger/' + repo_id + '/verify_pr/' + from + '/' + to + '/' + pr_id
                console.debug("PR Trigger Link URL: " + trigger_url)
                add_inner_html(pr_summary_panel[0], '<a href="'+trigger_url+'">Trigger PR Build</a>')

        })
})
