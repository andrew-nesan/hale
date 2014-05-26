/*
 * Copyright (c) 2014 Data Harmonisation Panel
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Data Harmonisation Panel <http://www.dhpanel.eu>
 */

package eu.esdihumboldt.hale.app.transform;

import static eu.esdihumboldt.hale.app.transform.ExecUtil.error;
import static eu.esdihumboldt.hale.app.transform.ExecUtil.fail;
import static eu.esdihumboldt.hale.app.transform.ExecUtil.info;
import static eu.esdihumboldt.hale.app.transform.ExecUtil.status;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.runtime.content.IContentType;

import com.google.common.util.concurrent.ListenableFuture;

import eu.esdihumboldt.hale.common.core.io.HaleIO;
import eu.esdihumboldt.hale.common.core.io.extension.IOProviderDescriptor;
import eu.esdihumboldt.hale.common.core.io.project.model.IOConfiguration;
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier;
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier;
import eu.esdihumboldt.hale.common.core.io.supplier.LocatableInputSupplier;
import eu.esdihumboldt.hale.common.core.io.supplier.LocatableOutputSupplier;
import eu.esdihumboldt.hale.common.core.report.Report;
import eu.esdihumboldt.hale.common.core.report.ReportHandler;
import eu.esdihumboldt.hale.common.headless.impl.ProjectTransformationEnvironment;
import eu.esdihumboldt.hale.common.headless.report.ReportFile;
import eu.esdihumboldt.hale.common.headless.transform.Transformation;
import eu.esdihumboldt.hale.common.instance.io.InstanceReader;
import eu.esdihumboldt.hale.common.instance.io.InstanceWriter;

/**
 * Executes a transformation.
 * 
 * @author Simon Templer
 */
public class ExecTransformation implements ConsoleConstants {

	/**
	 * ID for the transformation
	 */
	private final String id = UUID.randomUUID().toString();

	private ReportHandler reportHandler = null;

	private ProjectTransformationEnvironment env;

	private ExecContext context;

	private InstanceWriter target;

	private InstanceReader source;

	@SuppressWarnings("javadoc")
	public int run(ExecContext context) throws Exception {
		this.context = context;

		new ConsoleProgressManager();

		// set up report handler
		setupReportHandler();

		// set up transformation environment
		loadProject();

		// set up reader for source
		setupReader();

		// set up writer for target
		setupWriter();

		if (target == null) {
			// writer could not be created
			// return error code
			return 1;
		}

		// trigger transformation
		transform();

		// exit OK
		return 0;
	}

	private void setupReportHandler() {
		final ReportHandler delegateTo;
		if (context.getReportsOut() != null) {
			delegateTo = new ReportFile(context.getReportsOut());
		}
		else {
			delegateTo = null;
		}

		/*
		 * The report handler writes a summary to std out
		 */
		reportHandler = new ReportHandler() {

			@Override
			public void publishReport(Report<?> report) {
				ExecUtil.printSummary(report);
				if (delegateTo != null) {
					delegateTo.publishReport(report);
				}
			}
		};
	}

	private void loadProject() throws IOException {
		status("Loading HALE project...");

		env = new ProjectTransformationEnvironment(id, new DefaultInputSupplier(
				context.getProject()), reportHandler);
	}

	private void setupReader() {
		LocatableInputSupplier<? extends InputStream> sourceIn = new DefaultInputSupplier(
				context.getSource());

		// create I/O provider
		String customProvider = context.getSourceProviderId();
		if (customProvider != null) {
			// use specified provider
			source = HaleIO.createIOProvider(InstanceReader.class, null, customProvider);
			if (source == null) {
				fail("Could not find instance reader with ID " + customProvider);
			}
		}
		if (source == null) {
			// find applicable reader
			source = HaleIO.findIOProvider(InstanceReader.class, sourceIn, context.getSource()
					.getPath());
		}
		if (source == null) {
			fail("Could not determine instance reader to use for source data");
		}

		// apply custom settings
		source.loadConfiguration(context.getSourceSettings());

		source.setSource(sourceIn);
//		source.setCRSProvider(crsProvider)
		// source schema is set in Transformation.transform
	}

	private void setupWriter() {
		String preset = context.getPreset();
		String customProvider = context.getTargetProviderId();
		if (preset == null && customProvider == null) {
			fail("Please specify the name of a data export configuration preset or provide a specific provider ID for the instance writer");
		}

		// create I/O configuration
		IOConfiguration conf = null;
		if (preset != null) {
			conf = env.getExportPresets().get(preset);
		}
		if (conf == null) {
			if (customProvider == null) {
				throw fail("Data export configration preset not found: " + preset);
			}
			else {
				conf = new IOConfiguration();
			}
		}
		if (customProvider != null) {
			conf.setProviderId(customProvider);
		}

		// apply custom settings to configuration
		conf.getProviderConfiguration().putAll(context.getTargetSettings());

		// create I/O provider
		String writerId = conf.getProviderId();
		target = HaleIO.createIOProvider(InstanceWriter.class, null, writerId);
		target.setTarget(createTargetSupplier(context.getTarget()));
		target.setTargetSchema(env.getTargetSchema());

		// determine content type to use based on file extension
		IOProviderDescriptor factory = HaleIO.findIOProviderFactory(InstanceWriter.class, null,
				writerId);
		List<IContentType> cts = HaleIO.findContentTypesFor(factory.getSupportedTypes(), null,
				context.getTarget().getPath());
		if (!cts.isEmpty()) {
			target.setContentType(cts.get(0));
		}

		// apply configuration (may override content type)
		target.loadConfiguration(conf.getProviderConfiguration());
	}

	private LocatableOutputSupplier<? extends OutputStream> createTargetSupplier(final URI uri) {
		try {
			File file = new File(uri);
			return new FileIOSupplier(file);
		} catch (IllegalArgumentException e) {
			// TODO check for other supported URI types, e.g. FTP?

			// create dummy output supplier
			// e.g. for JDBC URIs
			return new LocatableOutputSupplier<OutputStream>() {

				@Override
				public OutputStream getOutput() throws IOException {
					throw new UnsupportedOperationException();
				}

				@Override
				public URI getLocation() {
					return uri;
				}
			};
		}
	}

	private void transform() throws InterruptedException, ExecutionException {
		status("Running HALE transformation...");

		List<InstanceReader> sources = new ArrayList<InstanceReader>();
		sources.add(source);
		ListenableFuture<Boolean> res = Transformation.transform(sources, target, env,
				reportHandler, id, null);

		if (res.get()) {
			info("Transformation completed. Please check the reports for more details.");
		}
		else {
			error("Transformation failed, please check the reports for details.");
		}
	}

}