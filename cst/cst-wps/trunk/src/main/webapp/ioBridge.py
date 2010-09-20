"""Humboldt IOBridge process"""

from pywps.Process import WPSProcess
from eu.esdihumboldt.cst.iobridge.impl import DefaultCstServiceBridge
import types
import os,urllib

def createIOBridgeProcess():

    class IOBridgeProcess(WPSProcess):
        def __init__(self):
            WPSProcess.__init__(self, identifier="iobridge",
                    title = "Humboldt CST IOBridge",
                    abstract = """IOBridge process for the Humbold CST.
                    Process acceptes schema file, oml file and input gml
                    file and provides the transformation""",
                    storeSupported = True,
                    statusSupported = True)

            self.schema = self.addComplexInput(identifier="schema",
                            title="Schema file",
                            formats = [{"mimeType":"text/xml"}])
            self.oml = self.addComplexInput(identifier="oml",
                            title="Ontology mapping file",
                            formats = [{"mimeType":"text/xml"}])
            self.gmlin = self.addComplexInput(identifier="gml",
                            title="Input GML file",
                            formats = [{"mimeType":"text/xml"}])
            self.gmlout = self.addComplexOutput(identifier="gml",
                            title="Output GML file",
                            formats = [{"mimeType":"text/xml"}])

        def execute(self):
            dcsb = DefaultCstServiceBridge()
            cwd = os.path.dirname(os.path.abspath(__file__))
            schemaPath = os.path.join(cwd, self.schema.getValue())
            omlPath = os.path.join(cwd, self.oml.getValue())
            gmlPath = os.path.join(cwd, self.gml.getValue())
            schemaUrl = urllib.basejoin("file:",schemaPath)
            omlUrl = urllib.basejoin("file:",omlPath)
            gmlUrl = urllib.basejoin("file:",gmlPath)
            outURL = urllib.basejoin("file:",os.path.abspath(os.path.dirname(__file__)))
            self.status.set("Transforming input GML")

            tempDir = os.path.join(cwd,"tmp")
            omlFile = os.path.basename(self.oml.getValue())
            gmlFile = os.path.basename(self.gmlin.getValue())

            transformedGML = None
            import java.lang.NullPointerException
            try:
                transformedGML = dcsb.transform(schemaUrl, omlUrl, gmlUrl,outURL)

            except java.lang.NullPointerException,e:
                return "Could not transform GML, got java.lang.NullPointerException: %s" % e
            self.gmlout.setValue(transformedGML)

    return IOBridgeProcess()
