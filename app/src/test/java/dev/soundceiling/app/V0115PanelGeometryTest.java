package dev.soundceiling.app;

import java.util.Arrays;
import java.util.List;

public final class V0115PanelGeometryTest {
    static final UserVolumeOverlayPlacement.Box PILL = new UserVolumeOverlayPlacement.Box(935,900,1035,1500);
    public static void main(String[] args) {
        FakeWindow other = new FakeWindow(9, false, PILL);
        FakeWindow volume = new FakeWindow(12, true, PILL);
        VolumePanelGeometry.Access access = new VolumePanelGeometry.Access() {
            public VolumePanelGeometry.Node source() { return null; } // Reproduce a source-less event.
            public List<VolumePanelGeometry.Window> windows() { return Arrays.asList(other, volume); }
        };
        VolumePanelGeometry.Result result = reader().find(access, 12);
        check(result.bounds == PILL && result.reason.equals("window_nodes"), "null source resolves the matching volume window");
        check(other.rootReads == 0 && volume.rootReads == 1, "another app root is never read");
        check(other.recycled && volume.recycled, "all window handles are released");
        volume.title = "";
        result = reader().find(access, -1);
        check(result.bounds == PILL, "narrow system volume window can be identified by verified SystemUI root without a title");
        volume.title = "VolumeDialog";
        volume.node = null;
        result = reader().find(access, 12);
        check(result.bounds == PILL && result.reason.equals("window_bounds"), "native window bounds work when nodes are unavailable");
        result = reader().find(access, -1);
        check(result.bounds == PILL && other.rootReads == 0, "source-less announcement resolves a titled system volume window only");
        volume.box = new UserVolumeOverlayPlacement.Box(0,0,1080,2400);
        result = reader().find(access, 12);
        check(result.bounds == null, "full-screen dialog bounds are not a capsule");
        int reads = volume.rootReads;
        result = new VolumePanelGeometry(1080,2400,2.8125f,()->0L,()->false).find(access,12);
        check(result.bounds == null && volume.rootReads == reads, "Stop cancels the query before root access");
        System.out.println("V0115PanelGeometryTest PASS");
    }
    static VolumePanelGeometry reader() { return new VolumePanelGeometry(1080,2400,2.8125f,()->0L,()->true); }
    static final class FakeWindow implements VolumePanelGeometry.Window {
        final int id; final boolean system; int rootReads; boolean recycled; String title;
        VolumePanelGeometry.Node node; UserVolumeOverlayPlacement.Box box;
        FakeWindow(int id,boolean system,UserVolumeOverlayPlacement.Box box) {
            this.id=id;this.system=system;this.box=box; title=system?"VolumeDialog":"App"; node=new FakeNode(system);
        }
        public int id() { return id; }
        public boolean system() { return system; }
        public String title() { return title; }
        public UserVolumeOverlayPlacement.Box bounds() { return box; }
        public VolumePanelGeometry.Node root() { rootReads++; return node; }
        public void recycle() { recycled=true; }
    }
    static final class FakeNode implements VolumePanelGeometry.Node {
        final boolean system; FakeNode(boolean system) { this.system=system; }
        public String packageName() { return system?"com.android.systemui":"other.app"; }
        public String className() { return "android.widget.SeekBar"; }
        public String resourceId() { return "com.android.systemui:id/volume_media"; }
        public boolean visible() { return true; }
        public UserVolumeOverlayPlacement.Box bounds() { return PILL; }
        public VolumePanelGeometry.Node parent() { return null; }
        public int childCount() { return 0; }
        public VolumePanelGeometry.Node child(int index) { return null; }
        public void recycle() {}
    }
    static void check(boolean ok,String why) { if(!ok)throw new AssertionError(why); }
}
