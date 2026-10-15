import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;

/**
 * Zefrix Studio - Brick Breaker
 * Single-file Java ME MIDlet. No external assets or libraries required.
 */
public class ZefrixBrickBreakerMIDlet extends MIDlet {
    private GameCanvas game;

    public void startApp() {
        if (game == null) game = new GameCanvas();
        game.startGameLoop();
        javax.microedition.lcdui.Display.getDisplay(this).setCurrent(game);
    }

    public void pauseApp() {
        if (game != null) game.stopGameLoop();
    }

    public void destroyApp(boolean unconditional) {
        if (game != null) game.stopGameLoop();
    }

    private class GameCanvas extends Canvas implements Runnable {
        private static final int BG = 0x141511;
        private static final int BODY_BG = 0x101114;
        private static final int WHITE = 0xF5F3E9;
        private static final int YELLOW = 0xF1E8B7;
        private static final int PALE = 0xDCD6A3;
        private static final int TEXT = 0xBCB9A4;
        private static final int BUTTON_BG = 0x24241F;
        private static final int BUTTON_BORDER = 0x77745D;
        private static final int BUTTON_DOWN = 0x4A4735;

        private int screenW, screenH, headerH, controlsH, fieldTop, fieldH;
        private double scale;
        private boolean running = false, gameOver = false, levelClear = false;
        private boolean left = false, right = false;
        private boolean loop = false;
        private Thread loopThread;
        private int score = 0, level = 1, lives = 3;
        private double paddleX, paddleY, paddleW, paddleH, paddleSpeed;
        private double ballX, ballY, ballR, ballVX, ballVY;
        private Brick[] bricks = new Brick[0];
        private Checkpoint checkpoint;
        private long lastTime;

        private class Brick {
            double x, y, w, h;
            int hp, max;
            Brick(int strength) { hp = strength; max = strength; }
        }

        private class Checkpoint {
            double x, y, vx, vy, paddleX;
            int score, level, lives;
            int[] hp, max;
        }

        GameCanvas() {
            setFullScreenMode(true);
            configureLayout();
            freshGame();
        }

        private int min(int a, int b) { return a < b ? a : b; }
        private int max(int a, int b) { return a > b ? a : b; }
        private double clamp(double v, double lo, double hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }

        private void configureLayout() {
            screenW = getWidth();
            screenH = getHeight();
            scale = screenW / 320.0;
            if (scale <= 0) scale = 1.0;
            headerH = max(20, (int)(42 * scale));
            controlsH = max(42, (int)(76 * scale));
            if (headerH + controlsH > screenH / 2) {
                headerH = max(16, screenH / 8);
                controlsH = max(34, screenH / 5);
            }
            fieldTop = headerH;
            fieldH = screenH - headerH - controlsH;
            if (fieldH < 40) fieldH = 40;
            paddleW = Math.min(68.0 * scale, screenW * 0.22);
            paddleH = Math.max(3.0, 7.0 * scale);
            paddleY = fieldH - 24.0 * scale;
            paddleSpeed = 280.0 * scale;
            ballR = Math.max(2.0, 5.0 * scale);
            layoutBricks();
        }

        private void layoutBricks() {
            double gap = 5.0 * scale;
            double margin = 12.0 * scale;
            int cols = 6;
            double bw = (screenW - margin * 2 - gap * (cols - 1)) / cols;
            for (int i = 0; i < bricks.length; i++) {
                int col = i % cols;
                int row = i / cols;
                bricks[i].x = margin + col * (bw + gap);
                bricks[i].y = 38.0 * scale + row * 20.0 * scale;
                bricks[i].w = bw;
                bricks[i].h = 13.0 * scale;
            }
        }

        private void makeBall() {
            ballX = paddleX + paddleW / 2.0;
            ballY = paddleY - 9.0 * scale;
            ballVX = 155.0 * scale;
            ballVY = -205.0 * scale;
        }

        private void makeLevel() {
            int count = (level == 1) ? 5 : min(6 + level * 2, 24);
            bricks = new Brick[count];
            for (int i = 0; i < count; i++) {
                int hp = 1;
                if (level >= 2 && i % 3 == 0) hp = 2;
                if (level >= 3 && i % 5 == 0) hp = 3;
                bricks[i] = new Brick(hp);
            }
            layoutBricks();
        }

        private void freshGame() {
            level = 1; score = 0; lives = 3;
            gameOver = false; levelClear = false; running = false;
            checkpoint = null;
            paddleX = (screenW - paddleW) / 2.0;
            paddleY = fieldH - 24.0 * scale;
            makeLevel();
            makeBall();
            repaint();
        }

        private void saveCheckpoint(boolean safeForContinue) {
            Checkpoint c = new Checkpoint();
            c.x = ballX;
            c.y = safeForContinue ? Math.min(ballY, paddleY - ballR - 2.0 * scale) : ballY;
            c.vx = ballVX;
            c.vy = safeForContinue ? -Math.abs(ballVY) : ballVY;
            c.paddleX = paddleX;
            c.score = score; c.level = level; c.lives = lives;
            c.hp = new int[bricks.length]; c.max = new int[bricks.length];
            for (int i = 0; i < bricks.length; i++) {
                c.hp[i] = bricks[i].hp; c.max[i] = bricks[i].max;
            }
            checkpoint = c;
        }

        private void continueGame() {
            if (gameOver) {
                if (checkpoint != null) {
                    score = checkpoint.score; level = checkpoint.level; lives = checkpoint.lives;
                    paddleX = clamp(checkpoint.paddleX, 0, screenW - paddleW);
                    makeLevel();
                    for (int i = 0; i < bricks.length && i < checkpoint.hp.length; i++) {
                        bricks[i].hp = checkpoint.hp[i]; bricks[i].max = checkpoint.max[i];
                    }
                    ballX = checkpoint.x; ballY = checkpoint.y;
                    ballVX = checkpoint.vx; ballVY = checkpoint.vy;
                    gameOver = false; levelClear = false; running = true;
                } else {
                    freshGame(); running = true;
                }
            } else if (levelClear) {
                level++; levelClear = false;
                makeLevel(); makeBall(); running = true;
            } else if (!running) {
                running = true;
            }
        }

        private void lose() {
            saveCheckpoint(true);
            running = false; gameOver = true;
        }

        private void update(double dt) {
            if (!running) return;
            if (left) paddleX -= paddleSpeed * dt;
            if (right) paddleX += paddleSpeed * dt;
            paddleX = clamp(paddleX, 0, screenW - paddleW);
            ballX += ballVX * dt;
            ballY += ballVY * dt;

            if (ballX - ballR < 0) { ballX = ballR; ballVX = Math.abs(ballVX); }
            if (ballX + ballR > screenW) { ballX = screenW - ballR; ballVX = -Math.abs(ballVX); }
            if (ballY - ballR < 0) { ballY = ballR; ballVY = Math.abs(ballVY); }

            if (ballVY > 0 && ballY + ballR >= paddleY && ballY - ballR <= paddleY + paddleH
                    && ballX >= paddleX && ballX <= paddleX + paddleW) {
                ballY = paddleY - ballR;
                double offset = (ballX - (paddleX + paddleW / 2.0)) / (paddleW / 2.0);
                double speed = Math.min(270.0 * scale, Math.sqrt(ballVX * ballVX + ballVY * ballVY) + 4.0 * scale);
                double angle = offset * 1.02;
                ballVX = speed * Math.sin(angle);
                ballVY = -Math.max(130.0 * scale, speed * Math.cos(angle));
            }

            for (int i = 0; i < bricks.length; i++) {
                Brick b = bricks[i];
                if (b.hp <= 0) continue;
                if (ballX + ballR > b.x && ballX - ballR < b.x + b.w
                        && ballY + ballR > b.y && ballY - ballR < b.y + b.h) {
                    b.hp--; score += 10;
                    double overlapX = Math.min(ballX + ballR - b.x, b.x + b.w - (ballX - ballR));
                    double overlapY = Math.min(ballY + ballR - b.y, b.y + b.h - (ballY - ballR));
                    if (overlapX < overlapY) ballVX *= -1; else ballVY *= -1;
                    break;
                }
            }
            if (ballY - ballR > fieldH) lose();
            boolean allGone = true;
            for (int i = 0; i < bricks.length; i++) if (bricks[i].hp > 0) { allGone = false; break; }
            if (allGone) {
                running = false; levelClear = true; saveCheckpoint(false);
            }
        }

        public void run() {
            lastTime = System.currentTimeMillis();
            while (loop) {
                long now = System.currentTimeMillis();
                double dt = (now - lastTime) / 1000.0;
                lastTime = now;
                if (dt > 0.025) dt = 0.025;
                if (dt < 0) dt = 0;
                update(dt);
                repaint();
                try { Thread.sleep(25); } catch (InterruptedException e) { }
            }
        }

        void startGameLoop() {
            if (loop) return;
            loop = true;
            loopThread = new Thread(this);
            loopThread.start();
        }

        void stopGameLoop() {
            loop = false;
            if (loopThread != null) {
                try { loopThread.join(100); } catch (InterruptedException e) { }
                loopThread = null;
            }
        }

        protected void paint(Graphics g) {
            configureLayout();
            g.setColor(BODY_BG); g.fillRect(0, 0, screenW, screenH);
            g.setColor(WHITE);
            g.setFont(g.getFont());
            g.drawString("Zefrix Studio", screenW / 2, headerH / 2,
                    Graphics.HCENTER | Graphics.VCENTER);

            g.setColor(BG); g.fillRect(0, fieldTop, screenW, fieldH);
            g.setClip(0, fieldTop, screenW, fieldH);
            g.setColor(0x24251F);
            int lineStep = max(10, (int)(25 * scale));
            for (int y = fieldTop + 20; y < fieldTop + fieldH; y += lineStep) {
                g.drawLine(0, y, screenW, y);
            }
            g.setColor(TEXT);
            g.drawString(String.valueOf(score), (int)(9 * scale), fieldTop + (int)(16 * scale), Graphics.TOP | Graphics.LEFT);
            g.drawString(String.valueOf(level), screenW - (int)(9 * scale), fieldTop + (int)(16 * scale), Graphics.TOP | Graphics.RIGHT);

            for (int i = 0; i < bricks.length; i++) {
                Brick b = bricks[i];
                if (b.hp <= 0) continue;
                g.setColor(b.hp > 1 ? YELLOW : WHITE);
                g.fillRect((int)b.x, fieldTop + (int)b.y, (int)b.w, (int)b.h);
                g.setColor(b.hp > 1 ? PALE : 0xD9D7CA);
                g.fillRect((int)b.x + (int)(2 * scale), fieldTop + (int)b.y + (int)(2 * scale),
                        max(1, (int)b.w - (int)(4 * scale)), max(1, (int)(2 * scale)));
                if (b.hp > 1) {
                    g.setColor(0x716B4C);
                    g.drawString(String.valueOf(b.hp), (int)(b.x + b.w / 2), fieldTop + (int)(b.y + 10 * scale),
                            Graphics.HCENTER | Graphics.TOP);
                }
            }
            g.setColor(WHITE);
            g.fillRect((int)paddleX, fieldTop + (int)paddleY, (int)paddleW, (int)paddleH);
            g.setColor(YELLOW);
            g.fillArc((int)(ballX - ballR), fieldTop + (int)(ballY - ballR),
                    (int)(ballR * 2), (int)(ballR * 2), 0, 360);
            g.setClip(0, 0, screenW, screenH);

            if (gameOver || levelClear) {
                g.setColor(0x101114); g.fillRect(0, fieldTop, screenW, fieldH);
                g.setColor(WHITE);
                g.setFont(g.getFont());
                g.drawString(gameOver ? "TRY AGAIN" : "LEVEL " + level + " CLEAR",
                        screenW / 2, fieldTop + fieldH / 2 - (int)(10 * scale),
                        Graphics.HCENTER | Graphics.VCENTER);
                g.setFont(javax.microedition.lcdui.Font.getFont(
                        javax.microedition.lcdui.Font.FACE_SYSTEM,
                        javax.microedition.lcdui.Font.STYLE_PLAIN,
                        javax.microedition.lcdui.Font.SIZE_SMALL));
                g.drawString(gameOver ? "2 CONTINUE  -  0 NEW GAME" : "2 NEXT LEVEL",
                        screenW / 2, fieldTop + fieldH / 2 + (int)(15 * scale),
                        Graphics.HCENTER | Graphics.VCENTER);
            }
            drawControls(g);
        }

        private void drawControls(Graphics g) {
            int top = fieldTop + fieldH;
            g.setColor(BODY_BG); g.fillRect(0, top, screenW, controlsH);
            int gap = max(3, (int)(8 * scale));
            int pad = max(4, (int)(10 * scale));
            int usable = screenW - 2 * pad - 3 * gap;
            int leftW = usable / 4;
            int midW = (int)(usable * 0.20);
            int rightW = usable - leftW - 2 * midW;
            int h = max(24, controlsH - max(12, (int)(20 * scale)));
            int y = top + (controlsH - h) / 2;
            int x = pad;
            drawButton(g, x, y, leftW, h, "<", left);
            x += leftW + gap;
            drawButton(g, x, y, midW, h, "2", false);
            x += midW + gap;
            drawButton(g, x, y, midW, h, "0", false);
            x += midW + gap;
            drawButton(g, x, y, rightW, h, ">", right);
        }

        private void drawButton(Graphics g, int x, int y, int w, int h, String label, boolean down) {
            g.setColor(down ? BUTTON_DOWN : BUTTON_BG);
            g.fillRoundRect(x, y, w, h, max(4, (int)(12 * scale)), max(4, (int)(12 * scale)));
            g.setColor(BUTTON_BORDER);
            g.drawRoundRect(x, y, w, h, max(4, (int)(12 * scale)), max(4, (int)(12 * scale)));
            g.setColor(0xF8F4D8);
            g.drawString(label, x + w / 2, y + h / 2, Graphics.HCENTER | Graphics.VCENTER);
        }

        protected void keyPressed(int keyCode) {
            int action = getGameAction(keyCode);
            if (action == LEFT || keyCode == KEY_NUM4) left = true;
            if (action == RIGHT || keyCode == KEY_NUM6) right = true;
            if (keyCode == KEY_NUM2) continueGame();
            if (keyCode == KEY_NUM0) freshGame();
            repaint();
        }

        protected void keyReleased(int keyCode) {
            int action = getGameAction(keyCode);
            if (action == LEFT || keyCode == KEY_NUM4) left = false;
            if (action == RIGHT || keyCode == KEY_NUM6) right = false;
        }
    }
}
