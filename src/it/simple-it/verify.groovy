import java.nio.charset.StandardCharsets
import java.nio.file.Files

def impactedTestsFile = new File(basedir, 'impacted_tests.lst')
assert impactedTestsFile.isFile()
String impactedTestsFileStr = new String(Files.readAllBytes(impactedTestsFile.toPath()), StandardCharsets.UTF_8)
assert impactedTestsFileStr.contains('Test')
