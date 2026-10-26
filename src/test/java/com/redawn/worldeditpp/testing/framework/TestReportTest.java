package com.redawn.common.test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestReportTest {
    @TempDir Path directory;

    @Test void suiteCountsEveryCaseAndFailure() throws Exception {
        Path file = directory.resolve("suite.xml");
        TestReport.writeSuite(file, "account & services", TestCategory.UNIT, List.of(
                TestResult.pass("account & services", "pass", 3),
                TestResult.fail("account & services", "fail", "blocked", 4, List.of())));
        var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
        assertEquals("2", xml.getDocumentElement().getAttribute("tests"));
        assertEquals("1", xml.getDocumentElement().getAttribute("failures"));
        assertEquals("UNIT", xml.getDocumentElement().getAttribute("category"));
        assertEquals(2, xml.getElementsByTagName("testcase").getLength());
        assertEquals(1, xml.getElementsByTagName("failure").getLength());
    }

    @Test void emptySuiteCannotReportSuccess() {
        assertThrows(IllegalArgumentException.class,
                () -> TestReport.writeSuite(directory.resolve("empty.xml"), "empty",
                        TestCategory.STRESS, List.of()));
    }

    @Test void successIsMachineReadable() throws Exception {
        Path file = directory.resolve("nested/result.xml");
        TestReport.write(file, "world & chat", TestCategory.CLIENT_SIMULATION, 1250, null);
        var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
        assertEquals("0", xml.getDocumentElement().getAttribute("failures"));
        assertEquals("CLIENT_SIMULATION", xml.getDocumentElement().getAttribute("category"));
        assertEquals(1, xml.getElementsByTagName("testcase").getLength());
        assertEquals("world & chat", xml.getElementsByTagName("testcase").item(0)
                .getAttributes().getNamedItem("name").getNodeValue());
    }

    @Test void failureCannotLeaveAnOldSuccessReport() throws Exception {
        Path file = directory.resolve("result.xml");
        TestReport.write(file, "first", TestCategory.SERVER_SIMULATION, 1, null);
        TestReport.write(file, "second", TestCategory.SERVER_SIMULATION, 2,
                new AssertionError("<failed> & \"quoted\""));
        var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
        assertEquals("1", xml.getDocumentElement().getAttribute("failures"));
        assertEquals(1, xml.getElementsByTagName("failure").getLength());
        assertTrue(xml.getElementsByTagName("failure").item(0)
                .getAttributes().getNamedItem("message").getNodeValue().contains("<failed> & \"quoted\""));
    }
}
