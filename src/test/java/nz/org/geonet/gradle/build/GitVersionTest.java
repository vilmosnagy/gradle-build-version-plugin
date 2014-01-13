package nz.org.geonet.gradle.build;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitVersionTest {

    static GitVersion buildVersion;

    @BeforeClass
    public static void setup() {
        buildVersion = new GitVersion("/home/geoffc/GeoNet/gradle-build-version-plugin");
    }

    @Test
    public void testGetLatestReleaseTag() throws Exception {
        Assert.assertTrue(buildVersion.getLatestReleaseTag("^release-\\d+\\.\\d+\\.\\d+$").matches("^release-\\d+\\.\\d+\\.\\d+$"));
        Assert.assertTrue(buildVersion.getLatestReleaseTag("^release-0.0.0$").matches("^release-0.0.0$"));
        Assert.assertTrue(buildVersion.getLatestReleaseTag("^(release-)(0.0.0)$").matches("^release-0.0.0$"));
    }

    @Test
    public void testGetNextSnapShotVersion() {
        Assert.assertEquals("0.0.1-SNAPSHOT", buildVersion.nextSnapShotVersion("release-0.0.0", "^(release-)(\\d+\\.\\d+\\.\\d)$", "$2", ".", "-SNAPSHOT"));
        Assert.assertEquals("0.0.1-SNAPSHOT", buildVersion.nextSnapShotVersion("release-0.0.0", "^release-(\\d+\\.\\d+\\.\\d)$", "$1", ".", "-SNAPSHOT"));
        Assert.assertEquals("0.1-SNAPSHOT", buildVersion.nextSnapShotVersion("release-0.0", "^(release-)(\\d+\\.\\d+)$", "$2", ".", "-SNAPSHOT"));
        Assert.assertEquals("1-SNAPSHOT", buildVersion.nextSnapShotVersion("release-0", "^(release-)(\\d+)$", "$2", ".", "-SNAPSHOT"));
    }

    @Test(expected = NumberFormatException.class)
    public void testGetNextSnapShotVersionException(){
        Assert.assertEquals("0.0.1-SNAPSHOT", buildVersion.nextSnapShotVersion("release-0.0.0", "^(release-)(\\d+\\.\\d+\\.\\d)$", "$2", "fred", "-SNAPSHOT"));
    }

    @Test
    public void testReleaseVersion() throws Exception {
        Assert.assertEquals("0.0.0", buildVersion.releaseVersion("release-0.0.0", "^release-(\\d+\\.\\d+\\.\\d)$", "$1"));
    }

    @Test
    public void testGetBuildVersion() throws Exception {
        Assert.assertTrue(buildVersion.getBuildVersion("^(release-)(\\d+\\.\\d+\\.\\d)$", "$2", ".", "-SNAPSHOT", false).matches("\\d+\\.\\d+\\.\\d+-SNAPSHOT$"));
        Assert.assertTrue(buildVersion.getBuildVersion("^(release-)(\\d+\\.\\d+\\.\\d)$", "$2", ".", "-SNAPSHOT", true).matches("\\d+\\.\\d+\\.\\d+$"));
    }
}
