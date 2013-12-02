package com.pmease.gitop.model.gatekeeper;

import com.pmease.commons.editable.annotation.Editable;
import com.pmease.gitop.model.PullRequest;
import com.pmease.gitop.model.gatekeeper.checkresult.CheckResult;

@SuppressWarnings("serial")
@Editable
public class NeverAccept extends AbstractGateKeeper {

	@Override
	public CheckResult check(PullRequest request) {
		return rejected("never");
	}

}