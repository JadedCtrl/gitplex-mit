package com.pmease.gitplex.web.component.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.shiro.SecurityUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;

import com.pmease.commons.git.GitText;
import com.pmease.commons.git.GitUtils;
import com.pmease.commons.hibernate.dao.Dao;
import com.pmease.commons.util.diff.DiffLine;
import com.pmease.commons.util.diff.DiffUtils;
import com.pmease.commons.util.diff.Partial;
import com.pmease.commons.util.diff.WordSplitter;
import com.pmease.commons.wicket.behavior.ConfirmBehavior;
import com.pmease.commons.wicket.behavior.ScrollBehavior;
import com.pmease.commons.wicket.behavior.StickyBehavior;
import com.pmease.commons.wicket.behavior.menu.CheckMenuItem;
import com.pmease.commons.wicket.behavior.menu.MenuBehavior;
import com.pmease.commons.wicket.behavior.menu.MenuItem;
import com.pmease.commons.wicket.behavior.menu.MenuPanel;
import com.pmease.gitplex.core.GitPlex;
import com.pmease.gitplex.core.manager.AuthorizationManager;
import com.pmease.gitplex.core.manager.CommitCommentManager;
import com.pmease.gitplex.core.manager.UserManager;
import com.pmease.gitplex.core.model.CommitComment;
import com.pmease.gitplex.core.model.Repository;
import com.pmease.gitplex.core.model.User;
import com.pmease.gitplex.core.permission.ObjectPermission;
import com.pmease.gitplex.web.component.comment.CommentInput;
import com.pmease.gitplex.web.component.label.AgeLabel;
import com.pmease.gitplex.web.component.markdown.MarkdownPanel;
import com.pmease.gitplex.web.component.user.AvatarMode;
import com.pmease.gitplex.web.component.user.UserLink;
import com.pmease.gitplex.web.model.UserModel;

@SuppressWarnings("serial")
public class TextDiffPanel extends Panel {

	private enum DiffOption {IGNORE_NOTHING, IGNORE_EOL, IGNORE_EOL_SPACES, IGNORE_CHANGE_SPACES};
	
	private static final int SCROLL_MARGIN = 50;
	
	private static final String COMMENTS_ROW_ID = "commentsRow";
	
	private static final String CONTENT_ROW_ID = "contentRow";
	
	private static final String COMMENT_ACTIONS_ID = "commentActions";
	
	private static final String NEW_COMMENT_ID = "newComment";
	
	private static final String HEAD_ID = "head";
	
	private final IModel<Repository> repoModel;
	
	private final BlobDiffInfo diffInfo;
	
	private final GitText oldText;
	
	private final GitText newText;
	
	private final IModel<Map<Integer, List<CommitComment>>> oldCommentsModel;
	
	private final IModel<Map<Integer, List<CommitComment>>> newCommentsModel;
	
	private final IModel<Boolean> allowToAddCommentModel;
	
	private GitText effectiveOldText;
	
	private GitText effectiveNewText;
	
	private DiffOption diffOption = DiffOption.IGNORE_NOTHING;
	
	private boolean showComments = true;
	
	private List<DiffLine> diffs;

	public TextDiffPanel(String id, final IModel<Repository> repoModel, 
			BlobDiffInfo diffInfo, GitText oldText, GitText newText) {
		super(id);
		
		this.repoModel = repoModel;
		
		String oldCommitHash = diffInfo.getOldRevision();
		if (!GitUtils.isHash(oldCommitHash))
			oldCommitHash = repoModel.getObject().git().parseRevision(oldCommitHash, true);
		String newCommitHash = diffInfo.getNewRevision();
		if (!GitUtils.isHash(newCommitHash))
			newCommitHash = repoModel.getObject().git().parseRevision(newCommitHash, true);
		
		this.diffInfo = new BlobDiffInfo(diffInfo.getStatus(), diffInfo.getOldPath(), diffInfo.getNewPath(), 
				diffInfo.getOldMode(), diffInfo.getNewMode(), oldCommitHash, newCommitHash);
		
		this.oldText = oldText;
		this.newText = newText;
		
		oldCommentsModel = new LoadableDetachableModel<Map<Integer, List<CommitComment>>>() {

			@Override
			protected Map<Integer, List<CommitComment>> load() {
				List<CommitComment> comments = GitPlex.getInstance(CommitCommentManager.class).findByCommitAndFile(
						TextDiffPanel.this.repoModel.getObject(), 
						TextDiffPanel.this.diffInfo.getOldRevision(), 
						TextDiffPanel.this.diffInfo.getOldPath());
				return mapCommentsToLines(comments);
			}
			
		};
		
		newCommentsModel = new LoadableDetachableModel<Map<Integer, List<CommitComment>>>() {

			@Override
			protected Map<Integer, List<CommitComment>> load() {
				List<CommitComment> comments = GitPlex.getInstance(CommitCommentManager.class).findByCommitAndFile(
						TextDiffPanel.this.repoModel.getObject(), 
						TextDiffPanel.this.diffInfo.getNewRevision(), 
						TextDiffPanel.this.diffInfo.getNewPath());
				return mapCommentsToLines(comments);
			}
			
		};

		// cache add comment permission check in model to avoid recalculation for every line
		allowToAddCommentModel = new LoadableDetachableModel<Boolean>() {

			@Override
			protected Boolean load() {
				User currentUser = GitPlex.getInstance(UserManager.class).getCurrent();
				ObjectPermission readPermission = ObjectPermission.ofRepositoryRead(repoModel.getObject());
				return currentUser != null && SecurityUtils.getSubject().isPermitted(readPermission);
			}
			
		};
		
		onDiffOptionChanged();
	}
	
	private Map<Integer, List<CommitComment>> mapCommentsToLines(List<CommitComment> comments) {
		Map<Integer, List<CommitComment>> map = new HashMap<>();
		for (CommitComment comment: comments) {
			List<CommitComment> lineComments = map.get(comment.getLine());
			if (lineComments == null) {
				lineComments = new ArrayList<>();
				map.put(comment.getLine(), lineComments);
			}
			lineComments.add(comment);
		}
		return map;
	}
	
	private void onDiffOptionChanged() {
		if (diffOption == DiffOption.IGNORE_EOL) {
			effectiveOldText = oldText.ignoreEOL();
			effectiveNewText = newText.ignoreEOL();
		} else if (diffOption == DiffOption.IGNORE_EOL_SPACES) {
			effectiveOldText = oldText.ignoreEOLSpaces();
			effectiveNewText = newText.ignoreEOLSpaces();
		} else if (diffOption == DiffOption.IGNORE_CHANGE_SPACES) {
			effectiveOldText = oldText.ignoreChangeSpaces();
			effectiveNewText = newText.ignoreChangeSpaces();
		} else {
			effectiveOldText = oldText;
			effectiveNewText = newText;
		}
		
		diffs = DiffUtils.diff(effectiveOldText.getLines(), effectiveNewText.getLines(), new WordSplitter());
	}

	private boolean isIdentical() {
		for (DiffLine diffLine: diffs) {
			if (diffLine.getAction() != DiffLine.Action.EQUAL)
				return false;
		}
		return true;
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		setOutputMarkupId(true);
		
		final WebMarkupContainer head = new WebMarkupContainer(HEAD_ID);
		
		head.add(new StickyBehavior());
		
		head.add(new DiffStatBar("diffStat", new AbstractReadOnlyModel<List<DiffLine>>() {

			@Override
			public List<DiffLine> getObject() {
				return diffs;
			}
			
		}) {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!isIdentical());
			}
			
		});
		add(head);
		
		List<String> alerts = new ArrayList<>();
		if (!oldText.getCharset().equals(newText.getCharset()))
			alerts.add("Charset is changed from " + oldText.getCharset() + " to " + newText.getCharset());
		if (!oldText.isHasEolAtEof())
			alerts.add("Original text does not have EOL character at EOF");
		if (!newText.isHasEolAtEof())
			alerts.add("Revised text does not have EOL character at EOF");
		
		head.add(new FileDiffTitle("title", diffInfo, alerts));
		
		head.add(new WebMarkupContainer("identical") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(isIdentical());
			}
			
		});
		
		head.add(new WebMarkupContainer("prevDiff") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!isIdentical());
			}
			
		}.add(new ScrollBehavior(head, ".diff-block", SCROLL_MARGIN, false)));

		head.add(new WebMarkupContainer("nextDiff") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!isIdentical());
			}
			
		}.add(new ScrollBehavior(head, ".diff-block", SCROLL_MARGIN, true)));
		
		// add a separate comment actions container in order not to refresh the whole
		// sticky head when show/hide comment actions (refreshing sticky head via Wicket 
		// ajax has some displaying issues) 
		WebMarkupContainer commentActions = new WebMarkupContainer(COMMENT_ACTIONS_ID);
		commentActions.setOutputMarkupId(true);
		
		head.add(commentActions);
		commentActions.add(new WebMarkupContainer("prevComment") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(showComments);
			}
			
		}.add(new ScrollBehavior(head, ".comments.line", 50, false)));
		
		commentActions.add(new WebMarkupContainer("nextComment") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(showComments);
			}
			
		}.add(new ScrollBehavior(head, ".comments.line", 50, true)));
		
		commentActions.add(new AjaxLink<Void>("showComments") {

			@Override
			public void onClick(AjaxRequestTarget target) {
				showComments(target);
			}
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!showComments);
			}

		});
		commentActions.add(new AjaxLink<Void>("hideComments") {

			@Override
			public void onClick(AjaxRequestTarget target) {
				hideComments(target);
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(showComments);
			}
			
		});

		MenuPanel diffOptionMenuPanel = new MenuPanel("diffOptions") {

			@Override
			protected List<MenuItem> getMenuItems() {
				List<MenuItem> menuItems = new ArrayList<>();
				
				menuItems.add(new CheckMenuItem() {
					
					@Override
					protected void onUpdate(AjaxRequestTarget target) {
						onDiffOptionChanged();
						close(target);
						target.add(TextDiffPanel.this);
					}
					
					@Override
					protected String getLabel() {
						return "Ignore end of line differences";
					}
					
					@Override
					protected IModel<Boolean> getCheckModel() {
						return new IModel<Boolean>() {

							@Override
							public void detach() {
							}

							@Override
							public Boolean getObject() {
								return diffOption == DiffOption.IGNORE_EOL;
							}

							@Override
							public void setObject(Boolean object) {
								if (object)
									diffOption = DiffOption.IGNORE_EOL;
								else
									diffOption = DiffOption.IGNORE_NOTHING;
							}
							
						};
					}
				});
				
				menuItems.add(new CheckMenuItem() {
					
					@Override
					protected void onUpdate(AjaxRequestTarget target) {
						onDiffOptionChanged();
						close(target);
						target.add(TextDiffPanel.this);
					}
					
					@Override
					protected String getLabel() {
						return "Ignore white spaces at line end";
					}
					
					@Override
					protected IModel<Boolean> getCheckModel() {
						return new IModel<Boolean>() {

							@Override
							public void detach() {
							}

							@Override
							public Boolean getObject() {
								return diffOption == DiffOption.IGNORE_EOL_SPACES;
							}

							@Override
							public void setObject(Boolean object) {
								if (object)
									diffOption = DiffOption.IGNORE_EOL_SPACES;
								else
									diffOption = DiffOption.IGNORE_NOTHING;
							}
							
						};
					}
				});

				menuItems.add(new CheckMenuItem() {
					
					@Override
					protected void onUpdate(AjaxRequestTarget target) {
						onDiffOptionChanged();
						close(target);
						target.add(TextDiffPanel.this);
					}
					
					@Override
					protected String getLabel() {
						return "Ignore white space changes";
					}
					
					@Override
					protected IModel<Boolean> getCheckModel() {
						return new IModel<Boolean>() {

							@Override
							public void detach() {
							}

							@Override
							public Boolean getObject() {
								return diffOption == DiffOption.IGNORE_CHANGE_SPACES;
							}

							@Override
							public void setObject(Boolean object) {
								if (object)
									diffOption = DiffOption.IGNORE_CHANGE_SPACES;
								else
									diffOption = DiffOption.IGNORE_NOTHING;
							}
							
						};
					}
				});

				return menuItems;
			}
			
		};
		
		head.add(diffOptionMenuPanel);
		
		head.add(new WebMarkupContainer("diffOptionsTrigger").add(new MenuBehavior(diffOptionMenuPanel)));
		
		add(new ListView<DiffLine>("lines", new AbstractReadOnlyModel<List<DiffLine>>() {

			@Override
			public List<DiffLine> getObject() {
				return diffs;
			}
			
		}) {

			@Override
			protected void populateItem(ListItem<DiffLine> lineItem) {
				final DiffLine diffLine = lineItem.getModelObject();
				final WebMarkupContainer contentRow = new WebMarkupContainer(CONTENT_ROW_ID);
				
				final IModel<List<CommitComment>> lineCommentsModel = new LoadableDetachableModel<List<CommitComment>>() {

					@Override
					protected List<CommitComment> load() {
						List<CommitComment> oldLineComments = oldCommentsModel.getObject().get(diffLine.getOldLineNo());
						if (oldLineComments == null)
							oldLineComments = new ArrayList<>();
						List<CommitComment> newLineComments = newCommentsModel.getObject().get(diffLine.getNewLineNo());
						if (newLineComments == null)
							newLineComments = new ArrayList<>();

						if (diffLine.getAction() == DiffLine.Action.DELETE) {
							return oldLineComments;
						} else if (diffLine.getAction() == DiffLine.Action.ADD) {
							return newLineComments;
						} else {
							List<CommitComment> comments = new ArrayList<>();
							comments.addAll(oldLineComments);
							comments.addAll(newLineComments);
							Collections.sort(comments);
							return comments;
						}
					}
					
				};
				
				final WebMarkupContainer commentsRow = new WebMarkupContainer(COMMENTS_ROW_ID) {

					@Override
					protected void onConfigure() {
						super.onConfigure();
						
						if (showComments) {
							if (isAddingComment(this))
								setVisible(true);
							else 
								setVisible(!lineCommentsModel.getObject().isEmpty());
						} else {
							setVisible(false);
						}
					}
					
				};
				
				commentsRow.setOutputMarkupId(true);
				commentsRow.add(new Label("commentCount", new AbstractReadOnlyModel<Integer> () {

					@Override
					public Integer getObject() {
						return lineCommentsModel.getObject().size();
					}
					
				}));
				commentsRow.add(new ListView<CommitComment>("comments", lineCommentsModel) {

					@Override
					protected void populateItem(final ListItem<CommitComment> commentItem) {
						CommitComment comment = commentItem.getModelObject();
						
						Fragment fragment = new Fragment("comment", "viewCommentFrag", TextDiffPanel.this);
						fragment.setOutputMarkupId(true);
						fragment.add(new UserLink("name", new UserModel(comment.getUser()), AvatarMode.NAME_AND_AVATAR));
						
						fragment.add(new AgeLabel("age", new AbstractReadOnlyModel<Date>() {

							@Override
							public Date getObject() {
								return commentItem.getModelObject().getDate();
							}
							
						}));
						
						fragment.add(new AjaxLink<Void>("edit") {

							@Override
							public void onClick(AjaxRequestTarget target) {
								Fragment fragment = new Fragment("comment", "editCommentFrag", TextDiffPanel.this);
								fragment.setOutputMarkupId(true);
								Form<?> form = new Form<Void>("form");
								form.setOutputMarkupId(true);
								final CommentInput input;
								form.add(input = new CommentInput("input", Model.of(commentItem.getModelObject().getContent())));
								form.add(new AjaxLink<Void>("cancel") {

									@Override
									public void onClick(AjaxRequestTarget target) {
										commentsRow.replace(new WebMarkupContainer(NEW_COMMENT_ID));
										target.add(commentsRow);
									}
									
								});
								form.add(new AjaxSubmitLink("save") {

									@Override
									protected void onError(AjaxRequestTarget target, Form<?> form) {
										super.onError(target, form);
										target.add(form);
									}

									@Override
									protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
										super.onSubmit(target, form);
										
										CommitComment comment = commentItem.getModelObject();
										comment.setDate(new Date());
										comment.setContent(input.getModelObject());
										
										GitPlex.getInstance(Dao.class).persist(comment);
										target.add(commentsRow);
									}

								});
								fragment.add(form);
								commentItem.replace(fragment);
								
								target.add(fragment);
							}

							@Override
							protected void onConfigure() {
								super.onConfigure();

								setVisible(GitPlex.getInstance(AuthorizationManager.class).canModify(commentItem.getModelObject()));
							}
							
						});
						final Long commentId = commentItem.getModelObject().getId();
						fragment.add(new AjaxLink<Void>("delete") {

							@Override
							public void onClick(AjaxRequestTarget target) {
								Dao dao = GitPlex.getInstance(Dao.class);
								dao.remove(dao.load(CommitComment.class, commentId));
								target.add(commentsRow);
							}
							
							@Override
							protected void onConfigure() {
								super.onConfigure();

								setVisible(GitPlex.getInstance(AuthorizationManager.class).canModify(commentItem.getModelObject()));
							}

						}.add(new ConfirmBehavior("Do you really want to delete this comment?")));
						
						fragment.add(new MarkdownPanel("content", Model.of(comment.getContent())));

						commentItem.add(fragment);
					}
					
				});

				commentsRow.add(new AddCommentLink("addComment", lineItem) {

					@Override
					protected void onConfigure() {
						super.onConfigure();
						
						if (isVisible() && isAddingComment(commentsRow))
							setVisible(false);
					}
					
				});
				
				commentsRow.add(new WebMarkupContainer(NEW_COMMENT_ID));
				
				lineItem.add(commentsRow);
				
				contentRow.add(new AddCommentLink("addComment", lineItem));
				
				if (diffLine.getAction() == DiffLine.Action.ADD) {
					if (lineItem.getIndex() == 0 || diffs.get(lineItem.getIndex()-1).getAction() == DiffLine.Action.EQUAL)
						contentRow.add(AttributeAppender.append("class", " new diff-block"));
					else
						contentRow.add(AttributeAppender.append("class", " new"));
					contentRow.add(new Label("oldLineNo"));
					contentRow.add(new Label("newLineNo", "+ " + (diffLine.getNewLineNo()+1)));
				} else if (diffLine.getAction() == DiffLine.Action.DELETE) {
					if (lineItem.getIndex() == 0 || diffs.get(lineItem.getIndex()-1).getAction() == DiffLine.Action.EQUAL)
						contentRow.add(AttributeAppender.append("class", " old diff-block"));
					else
						contentRow.add(AttributeAppender.append("class", " old"));
					contentRow.add(new Label("oldLineNo", "- " + (diffLine.getOldLineNo()+1)));
					contentRow.add(new Label("newLineNo"));
				} else {
					contentRow.add(AttributeAppender.append("class", " equal"));
					contentRow.add(new Label("oldLineNo", "  " + (diffLine.getOldLineNo()+1)));
					contentRow.add(new Label("newLineNo", "  " + (diffLine.getNewLineNo()+1)));
				}
				contentRow.add(new ListView<Partial>("partials", diffLine.getPartials()) {

					@Override
					protected void populateItem(ListItem<Partial> item) {
						Partial partial = item.getModelObject();
						Label label;
						if (partial.getContent().equals("\r"))
							label = new Label("partial", " ");
						else
							label = new Label("partial", partial.getContent());
						if (partial.isEmphasized())
							label.add(AttributeAppender.append("class", "emphasize"));
						item.add(label);
					}
					
				});
				contentRow.setOutputMarkupId(true);
				
				lineItem.add(contentRow);
			}
			
		});
	}

	private void showComments(AjaxRequestTarget target) {
		showComments = true;
		target.add(get(HEAD_ID).get(COMMENT_ACTIONS_ID));

		target.appendJavaScript("$('.comments.line').show();");
	}

	private void hideComments(AjaxRequestTarget target) {
		showComments = false;
		target.add(get(HEAD_ID).get(COMMENT_ACTIONS_ID));
		
		target.appendJavaScript("$('.comments.line').hide();");
	}
	
	private boolean isAddingComment(WebMarkupContainer commentsRow) {
		return commentsRow.get(NEW_COMMENT_ID) instanceof Fragment;
	}

	private class AddCommentLink extends AjaxLink<Void> {
		
		private ListItem<DiffLine> item;
		
		public AddCommentLink(String id, ListItem<DiffLine> item) {
			super(id);
			this.item = item;
		}
		
		@Override
		public void onClick(AjaxRequestTarget target) {
			if (!showComments)
				showComments(target);
			
			Fragment frag = new Fragment(NEW_COMMENT_ID, "newCommentFrag", TextDiffPanel.this);
			Form<?> form = new Form<Void>("form");
			form.setOutputMarkupId(true);
			
			final CommentInput input;
			form.add(input = new CommentInput("input", Model.of("")));
			
			final WebMarkupContainer contentRow = (WebMarkupContainer) item.get(CONTENT_ROW_ID);
			final WebMarkupContainer commentsRow = (WebMarkupContainer) item.get(COMMENTS_ROW_ID);
			final DiffLine diffLine = item.getModelObject();
			
			form.add(new AjaxLink<Void>("cancel") {

				@Override
				public void onClick(AjaxRequestTarget target) {
					commentsRow.replace(new WebMarkupContainer(NEW_COMMENT_ID));
					target.add(commentsRow);
				}
				
			});
			form.add(new AjaxSubmitLink("save") {

				@Override
				protected void onError(AjaxRequestTarget target, Form<?> form) {
					super.onError(target, form);
					target.add(form);
				}

				@Override
				protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
					super.onSubmit(target, form);
					CommitComment comment = new CommitComment();
					comment.setRepository(repoModel.getObject());
					comment.setUser(GitPlex.getInstance(UserManager.class).getCurrent());
					String commit;
					if (diffLine.getAction() == DiffLine.Action.ADD 
							|| diffLine.getAction() == DiffLine.Action.EQUAL) {
						commit = diffInfo.getNewRevision(); 
						comment.setFile(diffInfo.getNewPath());
						comment.setLine(diffLine.getNewLineNo());
					} else {
						commit = diffInfo.getOldRevision();
						comment.setFile(diffInfo.getOldPath());
						comment.setLine(diffLine.getOldLineNo());
					}
					comment.setCommit(commit);
					comment.setContent(input.getModelObject());
					comment.setDate(new Date());
					
					GitPlex.getInstance(Dao.class).persist(comment);
					
					commentsRow.replace(new WebMarkupContainer(NEW_COMMENT_ID));
					target.add(commentsRow);
				}

			});
			frag.add(form);
			commentsRow.replace(frag);
			if (!commentsRow.isVisible()) {
				String script = String.format("$(\"<tr id='%s'></tr>\").insertAfter('#%s');", 
						commentsRow.getMarkupId(), contentRow.getMarkupId());
				target.prependJavaScript(script);
				commentsRow.setVisible(true);
			} 
			target.add(commentsRow);
		}

		@Override
		protected void onConfigure() {
			super.onConfigure();
			
			setVisible(allowToAddCommentModel.getObject());
		}

	}

	@Override
	protected void onDetach() {
		oldCommentsModel.detach();
		newCommentsModel.detach();
		super.onDetach();
	}
	
}