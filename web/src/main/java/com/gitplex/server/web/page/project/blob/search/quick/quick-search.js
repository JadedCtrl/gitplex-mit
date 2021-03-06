gitplex.server.onQuickSearchDomReady = function(containerId, callback) {
	var $body = $("#" + containerId + ">.quick-search>.modal-body");
	
	var $input = $body.children("input");
	
	$input.doneEvents("inputchange", function() {
		callback("input", $(this).val());
	}, 100);

	function onReturn() {
		if (gitplex.server.form.confirmLeave()) {
			var $result = $body.children(".result");
			var $active = $result.find("li.hit.active");
			if ($active.length != 0) {
				callback("return", $active.index());
			}
		}
	}
	
	function onKeyup(e) {
		e.preventDefault();
		var $result = $body.children(".result");
		var $active = $result.find("li.hit.active");
		var $prev = $active.prev("li.hit");
		if ($prev.length != 0) {
			$active.removeClass("active");
			$prev.addClass("active");
		} 
		$result.scrollIntoView("li.hit.active", 36, 36);
	};
	
	function onKeydown(e) {
		e.preventDefault();
		var $result = $body.children(".result");
		var $active = $result.find("li.hit.active");
		var $next = $active.next("li.hit");
		if ($next.length != 0) {
			$active.removeClass("active");
			$next.addClass("active");
		} 
		$result.scrollIntoView("li.hit.active", 36, 36);
	};
	
	$body.children().bind("keydown", "return", onReturn);
	$body.children().bind("keydown", "up", onKeyup);
	$body.children().bind("keydown", "down", onKeydown);
	
};