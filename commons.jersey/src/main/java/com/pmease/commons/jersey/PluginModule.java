package com.pmease.commons.jersey;

import com.pmease.commons.loader.AbstractPlugin;
import com.pmease.commons.loader.AbstractPluginModule;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

/**
 * NOTE: Do not forget to rename moduleClass property defined in the pom if
 * you've renamed this class.
 * 
 */
public class PluginModule extends AbstractPluginModule {

	@Override
	protected void configure() {
		super.configure();

		// put your guice bindings here
		install(new JerseyServletModule() {

			@Override
			protected void configureServlets() {
				// Bind at least one resource here as otherwise Jersey will report error.
				bind(DummyResource.class);

				// Route all RESTful requests through GuiceContainer
				serve("/rest/*").with(GuiceContainer.class);
			}

		});
	}

	@Override
	protected Class<? extends AbstractPlugin> getPluginClass() {
		return Plugin.class;
	}

}
