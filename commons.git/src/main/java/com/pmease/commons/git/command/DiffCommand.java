import com.pmease.commons.git.TreeNode;
		cmd.addArgs("diff", fromRev + ".." + toRev, "--full-index", "--no-color", "--find-renames", 
				"--find-copies", "--src-prefix=#gitop_old/", "--dst-prefix=#gitop_new/");
					if (changeBuilder.newPath != null) 
					changeBuilder.type = null;
					changeBuilder.oldCommit = null;
					changeBuilder.newCommit = null;
					line = line.substring("diff --git #gitop_old/".length());
					
					changeBuilder.oldPath = StringUtils.substringBefore(line, " #gitop_new/");
					changeBuilder.newPath = StringUtils.substringAfter(line, " #gitop_new/");
				} else if (line.startsWith("deleted file mode ")) {
					changeBuilder.type = TreeNode.Type.fromMode(line.substring("deleted file mode ".length()));
				} else if (line.startsWith("new file mode ")) {
					changeBuilder.type = TreeNode.Type.fromMode(line.substring("new file mode ".length()));
				} else if (line.startsWith("rename from ") || line.startsWith("rename to ")) {
					changeBuilder.action = FileChange.Action.RENAME;
				} else if (line.startsWith("copy from ") || line.startsWith("copy to ")) {
					changeBuilder.action = FileChange.Action.COPY;
					changeBuilder.oldCommit = StringUtils.substringBefore(line, "..");
					changeBuilder.newCommit = StringUtils.substringAfter(line, "..");
					if (changeBuilder.newCommit.indexOf(' ') != -1) {
						changeBuilder.newCommit = StringUtils.substringBefore(changeBuilder.newCommit, " ");
						changeBuilder.type = TreeNode.Type.fromMode(StringUtils.substringAfterLast(line, " "));
					}
		if (changeBuilder.newPath != null)
		private String oldPath;
		
		private String newPath;
		private TreeNode.Type type;
		
		private String oldCommit;
		private String newCommit;
			return new FileChangeWithDiffs(action, oldPath, newPath, type, binary, 
					oldCommit, newCommit, DiffUtils.parseUnifiedDiff(diffLines));