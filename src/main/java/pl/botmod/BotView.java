package pl.botmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import pl.botmod.bot.BotClient;
import pl.botmod.bot.BotManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Funkcje tylko po stronie twojego klienta (bez OP): znacznik bota i kamera z jego oczu. */
public final class BotView {
    private static final Set<String> MARKED = Collections.synchronizedSet(new HashSet<>());
    private static String camName = null;
    private static final int[] PAL = {0xFF5865F2, 0xFF3BA55D, 0xFFE67E22, 0xFFE91E63, 0xFF1ABC9C, 0xFF9B59B6, 0xFFF1C40F, 0xFFE74C3C};

    private BotView() {}

    public static int color(String name) {
        return PAL[Math.floorMod(name.hashCode(), PAL.length)];
    }

    public static boolean isMarked(String name) { return MARKED.contains(name.toLowerCase()); }

    public static void toggleMark(String name) {
        String k = name.toLowerCase();
        if (!MARKED.remove(k)) MARKED.add(k);
    }

    public static boolean cameraOn(String name) { return camName != null && camName.equalsIgnoreCase(name); }

    /** Gracz o tej nazwie w TWOIM swiecie (tylko gdy jest w zasiegu renderowania). */
    public static AbstractClientPlayerEntity nearby(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && p.getName().getString().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    public static void toggleCamera(String name) {
        if (cameraOn(name)) {
            stopCamera();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            BotManager.log("Wejdź swoim klientem na ten sam serwer co bot.");
            return;
        }
        Entity e = nearby(name);
        if (e == null) {
            BotManager.log("Bot jest poza twoim zasięgiem - podejdź do niego (ten sam świat).");
            return;
        }
        mc.setCameraEntity(e);
        camName = name;
        BotManager.log("Kamera bota włączona. Wyjście: klawisz J.");
    }

    public static void stopCamera() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (camName != null && mc.player != null) mc.setCameraEntity(mc.player);
        camName = null;
    }

    public static void tick() {
        if (camName == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            camName = null;
            return;
        }
        Entity e = nearby(camName);
        if (e == null || e.isRemoved()) {
            stopCamera();
            BotManager.log("Kamera bota wyłączona (bot poza zasięgiem).");
        }
    }

    /** Rysuje na ekranie nazwe i odleglosc oznaczonych botow (widac je przez sciany). */
    public static void drawMarkers(DrawContext ctx) {
        if (MARKED.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        Camera cam = mc.gameRenderer.getCamera();
        if (!cam.isReady()) return;
        Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double cyw = Math.cos(yaw), syw = Math.sin(yaw), cpt = Math.cos(pitch), spt = Math.sin(pitch);
        double fx = -syw * cpt, fy = -spt, fz = cyw * cpt;
        double rx = -cyw, ry = 0, rz = -syw;
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        double fov = Math.toRadians(mc.options.getFov().getValue());
        double f = (h / 2.0) / Math.tan(fov / 2.0);
        TextRenderer tr = mc.textRenderer;

        for (BotClient b : BotManager.bots()) {
            if (!isMarked(b.getName())) continue;
            Vec3d t = null;
            AbstractClientPlayerEntity e = nearby(b.getName());
            if (e != null) {
                t = e.getPos().add(0, e.getHeight() + 0.4, 0);
            } else if (b.hasPosition()) {
                double[] p = b.getPosition();
                t = new Vec3d(p[0], p[1] + 2.2, p[2]);
            }
            if (t == null) continue;
            double dx = t.x - cp.x, dy = t.y - cp.y, dz = t.z - cp.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.5) continue;
            double z = dx * fx + dy * fy + dz * fz;
            double x = dx * rx + dy * ry + dz * rz;
            double y = dx * ux + dy * uy + dz * uz;
            double sx, sy;
            if (z > 0.05) {
                sx = w / 2.0 + x / z * f;
                sy = h / 2.0 - y / z * f;
            } else {
                sx = w / 2.0 + x * 1000;
                sy = h / 2.0 - y * 1000;
            }
            double vx = sx - w / 2.0, vy = sy - h / 2.0;
            double mx = w / 2.0 - 16, my = h / 2.0 - 16;
            if (Math.abs(vx) > mx || Math.abs(vy) > my) {
                double s = Math.min(mx / Math.max(1e-6, Math.abs(vx)), my / Math.max(1e-6, Math.abs(vy)));
                sx = w / 2.0 + vx * s;
                sy = h / 2.0 + vy * s;
            }
            int col = color(b.getName());
            int ix = (int) sx, iy = (int) sy;
            ctx.fill(ix - 4, iy - 4, ix + 4, iy + 4, 0xFF000000);
            ctx.fill(ix - 3, iy - 3, ix + 3, iy + 3, col);
            String label = b.getName() + "  " + (int) dist + " m";
            int tw = tr.getWidth(label);
            ctx.fill(ix - tw / 2 - 2, iy + 6, ix + tw / 2 + 2, iy + 17, 0x99000000);
            ctx.drawTextWithShadow(tr, label, ix - tw / 2, iy + 8, col);
        }
    }
}
