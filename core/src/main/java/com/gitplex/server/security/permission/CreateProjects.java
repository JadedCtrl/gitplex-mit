package com.gitplex.server.security.permission;

import org.apache.shiro.authz.Permission;

public class CreateProjects implements Permission {

	@Override
	public boolean implies(Permission p) {
		return p instanceof CreateProjects || p instanceof PublicPermission;
	}

}
