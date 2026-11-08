'use strict';

/**
 * The particle effects of the Cosmetics page, ported one to one from the mod's ParticleEffects.java
 * (mod/src/main/java/dev/lumina/client/feature/cosmetics): the same sprite atlas (Canvas instead of
 * Java2D), the same simulation at 20 ticks per second and the same drawing, as camera facing sprites
 * in the 3D scene of the player preview, so the player hides the particles behind it like in game.
 * Keep both files in step when an effect changes.
 */
(function () {
  const SPRITES = ['GLOW', 'SPARKLE', 'HEART', 'HEART_SHINE', 'SNOWFLAKE', 'SNOWFLAKE_2', 'SNOW_SOFT', 'PETAL', 'LEAF', 'BUTTERFLY', 'BUBBLE', 'DOT'];
  const S = Object.fromEntries(SPRITES.map((name) => [name, name]));
  const CELL = 128;
  const MAX_PARTICLES = 600;
  const PREVIEW_HEIGHT = 1.8;
  const TWO_PI = Math.PI * 2;
  const TICK_MS = 50;
  /** skinview3d's player is 32 units tall with the feet at y = -16; in game the model is 1.875 blocks tall. */
  const UNITS = 32 / 1.875;
  const FEET = -16;

  const PLAIN = 0, HEART = 1, FIREFLY = 2, BUTTERFLY = 3, STAR = 4, BUBBLE = 5, CRYSTAL = 6;

  // ------------------------------------------------------------------
  // sprite atlas (ParticleEffects.paint)
  // ------------------------------------------------------------------

  const white = (a) => `rgba(255,255,255,${a / 255})`;
  const gray = (v, a) => `rgba(${v},${v},${v},${a / 255})`;
  const CLEAR = white(0);

  function radial(g, cx, cy, r, stops) {
    const p = g.createRadialGradient(cx, cy, 0, cx, cy, r);
    for (const [at, color] of stops) p.addColorStop(at, color);
    return p;
  }

  function linear(g, x0, y0, c0, x1, y1, c1) {
    const p = g.createLinearGradient(x0, y0, x1, y1);
    p.addColorStop(0, c0);
    p.addColorStop(1, c1);
    return p;
  }

  function line(g, x0, y0, x1, y1) {
    g.beginPath();
    g.moveTo(x0, y0);
    g.lineTo(x1, y1);
    g.stroke();
  }

  function ellipse(g, x, y, w, h) {
    g.beginPath();
    g.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, TWO_PI);
    g.fill();
  }

  function stroke(g, width) {
    g.lineWidth = width;
    g.lineCap = 'round';
    g.lineJoin = 'round';
  }

  function heartPath(c) {
    const p = new Path2D();
    const m = c / 2;
    p.moveTo(m, c * 0.9);
    p.bezierCurveTo(c * 0.34, c * 0.76, c * 0.06, c * 0.56, c * 0.07, c * 0.34);
    p.bezierCurveTo(c * 0.08, c * 0.16, c * 0.24, c * 0.08, c * 0.33, c * 0.09);
    p.bezierCurveTo(c * 0.43, c * 0.1, m, c * 0.18, m, c * 0.27);
    p.bezierCurveTo(m, c * 0.18, c * 0.57, c * 0.1, c * 0.67, c * 0.09);
    p.bezierCurveTo(c * 0.76, c * 0.08, c * 0.92, c * 0.16, c * 0.93, c * 0.34);
    p.bezierCurveTo(c * 0.94, c * 0.56, c * 0.66, c * 0.76, m, c * 0.9);
    p.closePath();
    return p;
  }

  /** White or gray sprites (tinted by the particle color), each in a c x c canvas. */
  function paint(s, c = CELL) {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = c;
    const g = canvas.getContext('2d');
    const m = c / 2;
    switch (s) {
      case S.GLOW:
        g.fillStyle = radial(g, m, m, m, [[0, white(255)], [0.22, white(150)], [1, CLEAR]]);
        g.fillRect(0, 0, c, c);
        break;
      case S.SPARKLE: {
        g.fillStyle = radial(g, m, m, m * 0.6, [[0, white(150)], [1, CLEAR]]);
        g.fillRect(0, 0, c, c);
        const r = m * 0.95, w = m * 0.13;
        const star = new Path2D();
        star.moveTo(m, m - r);
        star.quadraticCurveTo(m + w, m - w, m + r, m);
        star.quadraticCurveTo(m + w, m + w, m, m + r);
        star.quadraticCurveTo(m - w, m + w, m - r, m);
        star.quadraticCurveTo(m - w, m - w, m, m - r);
        g.fillStyle = white(255);
        g.fill(star);
        break;
      }
      case S.HEART:
      case S.HEART_SHINE: {
        const heart = heartPath(c);
        if (s === S.HEART) {
          // lighter at the top, a touch deeper towards the tip
          g.fillStyle = linear(g, 0, c * 0.2, white(255), 0, c * 0.9, gray(200, 255));
          g.fill(heart);
          g.clip(heart);
          g.fillStyle = radial(g, m, c * 0.95, c * 0.55, [[0, gray(150, 120)], [1, gray(150, 0)]]);
          g.fillRect(0, 0, c, c);
        } else {
          g.clip(heart);
          g.fillStyle = radial(g, c * 0.32, c * 0.33, c * 0.2, [[0, white(235)], [0.5, white(120)], [1, CLEAR]]);
          ellipse(g, c * 0.14, c * 0.18, c * 0.36, c * 0.3);
          g.fillStyle = radial(g, c * 0.66, c * 0.3, c * 0.06, [[0, white(200)], [1, CLEAR]]);
          ellipse(g, c * 0.58, c * 0.22, c * 0.16, c * 0.16);
        }
        break;
      }
      case S.SNOWFLAKE: {
        g.strokeStyle = g.fillStyle = white(255);
        stroke(g, c * 0.055);
        for (let i = 0; i < 6; i++) {
          const a = (i * Math.PI) / 3 - Math.PI / 2;
          line(g, m, m, m + Math.cos(a) * m * 0.88, m + Math.sin(a) * m * 0.88);
          for (const at of [0.42, 0.66]) {
            const bx = m + Math.cos(a) * m * at, by = m + Math.sin(a) * m * at;
            const len = m * (at < 0.5 ? 0.26 : 0.18);
            for (let side = -1; side <= 1; side += 2) {
              const b = a + side * 0.75;
              line(g, bx, by, bx + Math.cos(b) * len, by + Math.sin(b) * len);
            }
          }
        }
        ellipse(g, m - c * 0.06, m - c * 0.06, c * 0.12, c * 0.12);
        break;
      }
      case S.SNOWFLAKE_2: {
        // a star plate: short arms with little diamonds at the tips
        g.strokeStyle = g.fillStyle = white(255);
        stroke(g, c * 0.06);
        g.beginPath();
        for (let i = 0; i < 6; i++) {
          const a = (i * Math.PI) / 3;
          const hx = m + Math.cos(a) * m * 0.3, hy = m + Math.sin(a) * m * 0.3;
          if (i === 0) g.moveTo(hx, hy);
          else g.lineTo(hx, hy);
        }
        g.closePath();
        g.stroke();
        for (let i = 0; i < 6; i++) {
          const a = (i * Math.PI) / 3 + Math.PI / 6;
          const sx = m + Math.cos(a) * m * 0.26, sy = m + Math.sin(a) * m * 0.26;
          const ex = m + Math.cos(a) * m * 0.7, ey = m + Math.sin(a) * m * 0.7;
          line(g, sx, sy, ex, ey);
          const tx = m + Math.cos(a) * m * 0.86, ty = m + Math.sin(a) * m * 0.86;
          const p = a + Math.PI / 2;
          const w = m * 0.09;
          g.beginPath();
          g.moveTo(ex, ey);
          g.lineTo(m + Math.cos(a) * m * 0.78 + Math.cos(p) * w, m + Math.sin(a) * m * 0.78 + Math.sin(p) * w);
          g.lineTo(tx, ty);
          g.lineTo(m + Math.cos(a) * m * 0.78 - Math.cos(p) * w, m + Math.sin(a) * m * 0.78 - Math.sin(p) * w);
          g.closePath();
          g.fill();
        }
        break;
      }
      case S.SNOW_SOFT:
        g.fillStyle = radial(g, m, m, m * 0.55, [[0, white(255)], [0.45, white(200)], [1, CLEAR]]);
        g.fillRect(0, 0, c, c);
        break;
      case S.PETAL: {
        // a cherry blossom petal: rounded with the little notch at the outer end
        const petal = new Path2D();
        petal.moveTo(m, c * 0.94);
        petal.bezierCurveTo(c * 0.1, c * 0.72, c * 0.06, c * 0.24, c * 0.37, c * 0.1);
        petal.quadraticCurveTo(c * 0.46, c * 0.07, m, c * 0.21);
        petal.quadraticCurveTo(c * 0.54, c * 0.07, c * 0.63, c * 0.1);
        petal.bezierCurveTo(c * 0.94, c * 0.24, c * 0.9, c * 0.72, m, c * 0.94);
        g.fillStyle = linear(g, 0, c * 0.94, gray(205, 255), 0, c * 0.15, white(255));
        g.fill(petal);
        g.clip(petal);
        g.fillStyle = radial(g, m, c * 0.92, c * 0.32, [[0, gray(170, 150)], [1, gray(170, 0)]]);
        g.fillRect(0, 0, c, c);
        g.strokeStyle = gray(225, 110);
        stroke(g, c * 0.018);
        line(g, m, c * 0.88, m, c * 0.4);
        break;
      }
      case S.LEAF: {
        // a pointed leaf with a stem and veins
        const leaf = new Path2D();
        leaf.moveTo(m, c * 0.06);
        leaf.bezierCurveTo(c * 0.86, c * 0.22, c * 0.86, c * 0.62, m, c * 0.84);
        leaf.bezierCurveTo(c * 0.14, c * 0.62, c * 0.14, c * 0.22, m, c * 0.06);
        g.fillStyle = linear(g, c * 0.25, c * 0.2, white(255), c * 0.75, c * 0.8, gray(190, 255));
        g.fill(leaf);
        g.strokeStyle = gray(150, 200);
        stroke(g, c * 0.025);
        line(g, m, c * 0.96, m, c * 0.16);
        stroke(g, c * 0.016);
        for (let i = 0; i < 3; i++) {
          const yy = c * (0.66 - i * 0.15);
          line(g, m, yy, m - c * 0.2, yy - c * 0.1);
          line(g, m, yy, m + c * 0.2, yy - c * 0.1);
        }
        break;
      }
      case S.BUTTERFLY: {
        // two pairs of wings, lighter inside, with a few spots; the body is dark
        const inner = white(255), outer = gray(170, 255);
        for (let side = -1; side <= 1; side += 2) {
          const upper = new Path2D();
          upper.moveTo(m, c * 0.46);
          upper.bezierCurveTo(m + side * c * 0.12, c * 0.1, m + side * c * 0.5, c * 0.02, m + side * c * 0.47, c * 0.3);
          upper.bezierCurveTo(m + side * c * 0.45, c * 0.46, m + side * c * 0.2, c * 0.5, m, c * 0.52);
          g.fillStyle = radial(g, m + side * c * 0.22, c * 0.32, c * 0.3, [[0, inner], [1, outer]]);
          g.fill(upper);
          const lower = new Path2D();
          lower.moveTo(m, c * 0.52);
          lower.bezierCurveTo(m + side * c * 0.3, c * 0.5, m + side * c * 0.4, c * 0.72, m + side * c * 0.26, c * 0.82);
          lower.bezierCurveTo(m + side * c * 0.14, c * 0.9, m + side * c * 0.05, c * 0.7, m, c * 0.6);
          g.fillStyle = radial(g, m + side * c * 0.18, c * 0.64, c * 0.24, [[0, inner], [1, outer]]);
          g.fill(lower);
          g.fillStyle = white(230);
          ellipse(g, m + side * c * 0.33 - c * 0.04, c * 0.2, c * 0.08, c * 0.08);
          ellipse(g, m + side * c * 0.22 - c * 0.03, c * 0.68, c * 0.06, c * 0.06);
        }
        g.strokeStyle = g.fillStyle = gray(70, 255);
        ellipse(g, m - c * 0.035, c * 0.3, c * 0.07, c * 0.46);
        stroke(g, c * 0.015);
        line(g, m - c * 0.01, c * 0.31, m - c * 0.09, c * 0.18);
        line(g, m + c * 0.01, c * 0.31, m + c * 0.09, c * 0.18);
        break;
      }
      case S.BUBBLE:
        // see-through middle, a bright rim and a highlight
        g.fillStyle = radial(g, m, m, m * 0.92, [[0, white(18)], [0.7, white(45)], [0.9, white(210)], [1, CLEAR]]);
        g.fillRect(0, 0, c, c);
        g.fillStyle = radial(g, c * 0.34, c * 0.32, c * 0.16, [[0, white(240)], [1, CLEAR]]);
        ellipse(g, c * 0.16, c * 0.16, c * 0.36, c * 0.32);
        g.fillStyle = radial(g, c * 0.68, c * 0.7, c * 0.08, [[0, white(150)], [1, CLEAR]]);
        ellipse(g, c * 0.58, c * 0.6, c * 0.2, c * 0.2);
        break;
      case S.DOT:
        g.fillStyle = radial(g, m, m, m * 0.5, [[0, white(255)], [0.6, white(255)], [1, CLEAR]]);
        g.fillRect(0, 0, c, c);
        break;
    }
    return canvas;
  }

  // ------------------------------------------------------------------
  // simulation (ParticleEffects.Emitter)
  // ------------------------------------------------------------------

  const rnd = Math.random;
  const nextInt = (n) => Math.floor(rnd() * n);
  const around = (r) => (rnd() * 2 - 1) * r;
  const range = (min, max) => min + rnd() * (max - min);
  const pick = (...colors) => colors[nextInt(colors.length)];
  const nextBool = () => rnd() < 0.5;

  function make(sprite, x, y, z, life, size, color) {
    return {
      x, y, z, px: x, py: y, pz: z, vx: 0, vy: 0, vz: 0,
      size, rot: rnd() * TWO_PI, spin: 0, gravity: 0, drag: 0.96,
      age: 0, life, color, sprite,
      flip: rnd() * TWO_PI, flipSpeed: 0,
      swayAmp: 0, swayFreq: 0, swayPhase: rnd() * TWO_PI,
      pop: false, blink: 0, kind: PLAIN,
    };
  }

  class Emitter {
    constructor() {
      this.particles = [];
      this.ticks = 0;
      this.density = 1;
      this.tint = 0xffb9c6ff;
    }

    chance(perTick) {
      return rnd() < perTick * this.density;
    }

    count(perTick) {
      const n = perTick * this.density;
      const whole = Math.floor(n);
      return whole + (rnd() < n - whole ? 1 : 0);
    }

    push(q) {
      if (this.particles.length >= MAX_PARTICLES) this.particles.shift();
      this.particles.push(q);
    }

    add(sprite, x, y, z, life, size, color) {
      const q = make(sprite, x, y, z, life, size, color);
      this.push(q);
      return q;
    }

    countKind(kind) {
      let n = 0;
      for (const q of this.particles) if (q.kind === kind) n++;
      return n;
    }

    update(ox, oy, oz) {
      this.ticks++;
      let born = null;
      for (let i = this.particles.length - 1; i >= 0; i--) {
        const q = this.particles[i];
        q.px = q.x;
        q.py = q.y;
        q.pz = q.z;
        if (q.kind === BUTTERFLY) this.steerButterfly(q, ox, oy, oz);
        if (q.kind === FIREFLY) {
          q.vx += around(0.0025);
          q.vy += around(0.0018);
          q.vz += around(0.0025);
        }
        q.x += q.vx;
        q.y += q.vy;
        q.z += q.vz;
        if (q.swayAmp > 0) {
          // moves along a circle, so the sway never drifts away
          const a0 = q.swayPhase + q.age * q.swayFreq, a1 = a0 + q.swayFreq;
          q.x += q.swayAmp * (Math.sin(a1) - Math.sin(a0));
          q.z += q.swayAmp * (Math.cos(a1) - Math.cos(a0));
        }
        q.vx *= q.drag;
        q.vy = q.vy * q.drag - q.gravity;
        q.vz *= q.drag;
        q.rot += q.spin;
        q.flip += q.flipSpeed;
        q.age++;
        if (q.kind === STAR && q.age < q.life) (born = born || []).push(this.trail(q));
        if (q.age >= q.life) {
          this.particles.splice(i, 1);
          if (q.kind === STAR || q.kind === BUBBLE) this.burst((born = born || []), q);
        }
      }
      if (born) for (const q of born) this.push(q);
    }

    steerButterfly(q, ox, oy, oz) {
      // flutter on a wandering heading and turn back when too far from you
      const dx = q.x - ox, dz = q.z - oz;
      const dist = Math.sqrt(dx * dx + dz * dz);
      q.rot += around(0.35);
      let heading = q.rot;
      if (dist > 1.25) heading = Math.atan2(-dz, -dx);
      else if (dist < 0.45) heading = Math.atan2(dz, dx);
      q.vx = q.vx * 0.8 + Math.cos(heading) * 0.03 * 0.2;
      q.vz = q.vz * 0.8 + Math.sin(heading) * 0.03 * 0.2;
      const targetY = oy + 0.9 + 0.5 * Math.sin(q.age * 0.03 + q.swayPhase);
      q.vy = q.vy * 0.85 + (targetY - q.y) * 0.01 + Math.sin(q.age * 0.6 + q.swayPhase) * 0.006;
    }

    trail(star) {
      const t = make(S.GLOW, star.x, star.y, star.z, 9, star.size * 0.85, 0xffffc966);
      t.rot = 0;
      t.drag = 0.8;
      return t;
    }

    burst(out, from) {
      const n = from.kind === STAR ? 5 : 4;
      for (let i = 0; i < n; i++) {
        const s = make(S.DOT, from.x, from.y, from.z, 5 + nextInt(4), 0.025 + rnd() * 0.015, from.kind === STAR ? 0xfffff1c4 : 0xfff2fbff);
        const a = rnd() * Math.PI * 2;
        s.vx = Math.cos(a) * 0.035;
        s.vz = Math.sin(a) * 0.035;
        s.vy = around(0.03) + (from.kind === STAR ? 0.02 : 0);
        s.drag = 0.8;
        out.push(s);
      }
    }

    spawn(effect, x, y, z, h) {
      switch (effect) {
        case 'hearts': {
          if (this.chance(0.1)) {
            const a = rnd() * Math.PI * 2, r = range(0.35, 0.6);
            const q = this.add(S.HEART, x + Math.cos(a) * r, y + h * range(0.55, 1.0), z + Math.sin(a) * r, 48 + nextInt(16),
              range(0.13, 0.2), pick(0xffff4d7a, 0xffff6fa3, 0xffe8304f, 0xffff8fb8));
            q.kind = HEART;
            q.vy = 0.016 + rnd() * 0.01;
            q.drag = 0.985;
            q.swayAmp = 0.06;
            q.swayFreq = range(0.08, 0.13);
            q.rot = range(-0.25, 0.25);
            q.spin = range(-0.004, 0.004);
            q.pop = true;
          }
          if (this.chance(0.04)) {
            const s = this.add(S.SPARKLE, x + around(0.55), y + h * range(0.5, 1.05), z + around(0.55), 14, range(0.04, 0.06), 0xffffe3ee);
            s.vy = 0.01;
            s.spin = 0.08;
          }
          break;
        }
        case 'sakura':
          for (let i = this.count(0.38); i > 0; i--) {
            const a = rnd() * Math.PI * 2, r = range(0.2, 0.95);
            const q = this.add(S.PETAL, x + Math.cos(a) * r, y + h + range(0.25, 0.7), z + Math.sin(a) * r, 80 + nextInt(25),
              range(0.07, 0.11), pick(0xffffb7ce, 0xffffc9d9, 0xfff79ab8, 0xffffdde7, 0xfffff0f4));
            // a slow swirl around you while they fall
            q.vx = -Math.sin(a) * 0.008;
            q.vz = Math.cos(a) * 0.008;
            q.vy = -0.008;
            q.gravity = 0.00035;
            q.drag = 0.982;
            q.swayAmp = range(0.18, 0.32);
            q.swayFreq = range(0.06, 0.11);
            q.flipSpeed = range(0.07, 0.17) * (nextBool() ? 1 : -1);
            q.spin = range(-0.07, 0.07);
          }
          break;
        case 'snow':
          for (let i = this.count(0.95); i > 0; i--) {
            const a = rnd() * Math.PI * 2, r = Math.sqrt(rnd()) * 1.15;
            const crystal = rnd() < 0.45;
            const q = this.add(crystal ? (nextBool() ? S.SNOWFLAKE : S.SNOWFLAKE_2) : S.SNOW_SOFT,
              x + Math.cos(a) * r, y + h + range(0.55, 0.95), z + Math.sin(a) * r, 75 + nextInt(20),
              crystal ? range(0.065, 0.11) : range(0.035, 0.06), crystal ? 0xffeaf4ff : 0xe6ffffff);
            q.vy = -range(0.01, 0.018);
            q.gravity = 0.0004;
            q.drag = 0.985;
            q.swayAmp = range(0.08, 0.16);
            q.swayFreq = range(0.06, 0.12);
            q.spin = range(-0.04, 0.04);
            if (crystal) {
              q.kind = CRYSTAL;
              q.flipSpeed = range(0.03, 0.09);
              q.blink = range(0.25, 0.45);
            }
          }
          break;
        case 'autumn':
          for (let i = this.count(0.3); i > 0; i--) {
            const a = rnd() * Math.PI * 2, r = range(0.2, 1.0);
            const q = this.add(S.LEAF, x + Math.cos(a) * r, y + h + range(0.3, 0.7), z + Math.sin(a) * r, 80 + nextInt(25),
              range(0.09, 0.14), pick(0xffe8762b, 0xffd94a1e, 0xfff2b33d, 0xffc5702a, 0xffc93c20, 0xffe89a2f));
            q.vy = -0.01;
            q.gravity = 0.00045;
            q.drag = 0.98;
            q.swayAmp = range(0.22, 0.38);
            q.swayFreq = range(0.05, 0.09);
            q.flipSpeed = range(0.06, 0.14) * (nextBool() ? 1 : -1);
            q.spin = range(-0.09, 0.09);
          }
          break;
        case 'butterflies':
          if (this.countKind(BUTTERFLY) < Math.round(3 * this.density) && this.chance(0.04)) {
            const a = rnd() * Math.PI * 2;
            const q = this.add(S.BUTTERFLY, x + Math.cos(a) * 0.9, y + h * range(0.4, 0.9), z + Math.sin(a) * 0.9, 220 + nextInt(120),
              range(0.16, 0.2), pick(0xff9fd8ff, 0xffffb86b, 0xffe3a6ff, 0xffffe680, 0xffff9cc2));
            q.kind = BUTTERFLY;
            q.rot = a + Math.PI;
            q.drag = 1;
          }
          break;
        case 'fireflies':
          if (this.countKind(FIREFLY) < Math.round(14 * this.density) && this.chance(0.2)) {
            const a = rnd() * Math.PI * 2, r = range(0.45, 1.1);
            const q = this.add(S.GLOW, x + Math.cos(a) * r, y + range(0.15, h + 0.3), z + Math.sin(a) * r, 90 + nextInt(60),
              range(0.13, 0.18), pick(0xffe4ff7a, 0xffd2ff5e, 0xfffff27a));
            q.kind = FIREFLY;
            q.drag = 0.92;
            q.blink = range(0.06, 0.12);
            q.rot = 0;
          }
          break;
        case 'bubbles':
          for (let i = this.count(0.16); i > 0; i--) {
            const a = rnd() * Math.PI * 2, r = range(0.35, 0.75);
            const q = this.add(S.BUBBLE, x + Math.cos(a) * r, y + range(0.1, h * 0.7), z + Math.sin(a) * r, 45 + nextInt(35),
              range(0.06, 0.13), pick(0xffbdebff, 0xffd9c8ff, 0xffffd6f0, 0xffc8fff0, 0xfffff4c8));
            q.kind = BUBBLE;
            q.vy = range(0.012, 0.02);
            q.drag = 0.99;
            q.swayAmp = range(0.05, 0.1);
            q.swayFreq = range(0.1, 0.18);
            q.rot = range(-0.3, 0.3);
            q.pop = true;
          }
          break;
        case 'starfall':
          if (this.chance(0.09)) {
            const a = rnd() * Math.PI * 2, r = range(0.5, 1.2);
            const dir = rnd() * Math.PI * 2;
            const q = this.add(S.SPARKLE, x + Math.cos(a) * r, y + h + range(1.0, 1.6), z + Math.sin(a) * r, 16 + nextInt(6),
              range(0.12, 0.15), pick(0xfffff6d5, 0xffffffff, 0xffffe9a8));
            q.kind = STAR;
            q.vx = Math.cos(dir) * 0.045;
            q.vz = Math.sin(dir) * 0.045;
            q.vy = -0.1;
            q.drag = 1;
            q.spin = 0.2;
          }
          break;
        case 'sparks':
          for (let i = this.count(0.9); i > 0; i--) {
            const a = rnd() * Math.PI * 2;
            const q = this.add(S.DOT, x + Math.cos(a) * 0.45, y + rnd() * h, z + Math.sin(a) * 0.45, 6 + nextInt(6),
              0.05 + rnd() * 0.04, this.tint);
            q.vx = around(0.06);
            q.vy = around(0.06);
            q.vz = around(0.06);
            q.drag = 0.8;
          }
          break;
        case 'galaxy':
          // stars drifting off the spiral (the spiral itself is drawn every frame)
          if (this.chance(0.5)) {
            const a = rnd() * Math.PI * 2;
            const q = this.add(S.SPARKLE, x + Math.cos(a) * 0.6, y + rnd() * h, z + Math.sin(a) * 0.6, 30,
              0.06 + rnd() * 0.05, pick(0xffb9a6ff, 0xff7fb6ff, 0xffff9be8, 0xffffffff));
            q.vx = Math.cos(a) * 0.01;
            q.vz = Math.sin(a) * 0.01;
            q.vy = 0.004;
          }
          break;
        default:
      }
    }
  }

  // ------------------------------------------------------------------
  // drawing (ParticleEffects.drawParticles / drawShapes)
  // ------------------------------------------------------------------

  function popScale(t) {
    // ease out with a small overshoot
    const c = 1.9;
    const k = t - 1;
    return 1 + (c + 1) * k * k * k + c * k * k;
  }

  const lerp = (t, a, b) => a + (b - a) * t;

  function drawParticles(out, emitter, partial) {
    for (const q of emitter.particles) {
      const age = q.age + partial;
      const t = age / q.life;
      let fade = t < 0.12 ? t / 0.12 : 1 - Math.max(0, (t - 0.65) / 0.35);
      // bubbles and shooting stars stay bright until they pop
      if (q.kind === BUBBLE || q.kind === STAR) fade = Math.min(1, t / 0.12);
      const x = lerp(partial, q.px, q.x), y = lerp(partial, q.py, q.y), z = lerp(partial, q.pz, q.z);
      let size = q.size;
      if (q.pop) size *= popScale(Math.min(1, age / 7));
      if (q.blink > 0) {
        const b = Math.sin(age * q.blink + q.swayPhase);
        fade *= q.kind === FIREFLY ? 0.12 + 0.88 * b * b : 0.7 + 0.3 * b;
      }
      const rot = q.rot + q.spin * partial;
      const width = q.flipSpeed !== 0 ? 0.2 + 0.8 * Math.abs(Math.cos(q.flip + q.flipSpeed * partial)) : 1;
      switch (q.kind) {
        case HEART:
          size *= 1 + 0.07 * Math.sin(age * 0.45); // a soft heartbeat
          out(x, y, z, size * 2.1, 1, rot, S.GLOW, q.color, fade * 0.28);
          out(x, y, z, size, 1, rot, S.HEART, q.color, fade);
          out(x, y, z, size, 1, rot, S.HEART_SHINE, 0xffffffff, fade * 0.8);
          break;
        case FIREFLY:
          out(x, y, z, size, 1, 0, S.GLOW, q.color, fade * 0.8);
          out(x, y, z, size * 0.28, 1, 0, S.DOT, 0xffffffe6, fade);
          break;
        case BUTTERFLY: {
          // wings open and close; the sprite stays upright
          const flap = 0.18 + 0.82 * Math.abs(Math.sin(age * 0.85 + q.swayPhase));
          const tilt = 0.25 * Math.sin(age * 0.05 + q.swayPhase);
          out(x, y, z, size, flap, tilt, S.BUTTERFLY, q.color, fade);
          break;
        }
        case STAR:
          out(x, y, z, size * 2.4, 1, 0, S.GLOW, 0xffffe2a0, fade * 0.45);
          out(x, y, z, size, 1, rot, S.SPARKLE, q.color, fade);
          break;
        case BUBBLE:
          out(x, y, z, size * (1 + 0.25 * t), 1, rot, S.BUBBLE, q.color, fade);
          break;
        default:
          out(x, y, z, size, width, rot, q.sprite, q.color, fade);
      }
    }
  }

  /** Effects drawn as shapes every frame (not simulated), around a player at (x, y, z). */
  function drawShapes(out, effect, x, y, z, h, tint) {
    const time = (Date.now() % 100000) / 1000;
    if (effect === 'orbit') {
      for (let i = 0; i < 3; i++) {
        for (let trail = 0; trail < 6; trail++) {
          const a = time * 2.4 - trail * 0.12 + (i * Math.PI * 2) / 3;
          const yy = y + h * 0.55 + Math.sin(time * 1.6 + i * 2) * 0.25;
          const size = 0.11 * (1 - trail / 7);
          out(x + Math.cos(a) * 0.62, yy, z + Math.sin(a) * 0.62, size, 1, time * 2, trail === 0 ? S.SPARKLE : S.GLOW, tint, 1 - trail / 6);
        }
      }
    } else if (effect === 'galaxy') {
      const colors = [0xffb9a6ff, 0xff7fb6ff, 0xffff9be8];
      const n = 26;
      for (let arm = 0; arm < 2; arm++) {
        for (let i = 0; i < n; i++) {
          const k = i / n;
          const a = time * 1.5 + k * Math.PI * 3 + arm * Math.PI;
          const r = 0.45 + 0.15 * Math.sin(time + k * 6);
          out(x + Math.cos(a) * r, y + k * h * 1.05, z + Math.sin(a) * r, 0.05 + 0.03 * Math.sin(time * 3 + i), 1, time, S.SPARKLE, colors[(i + arm) % 3], 0.9);
        }
      }
    }
  }

  // ------------------------------------------------------------------
  // the preview: sprites in the skinview3d scene
  // ------------------------------------------------------------------

  /**
   * Attaches the effect to a skinview3d SkinViewer. three.js lives inside the skinview3d bundle; its
   * Sprite classes are reached through NameTagObject, which is a Sprite with a SpriteMaterial and a
   * CanvasTexture.
   */
  class Preview {
    constructor(viewer, skinview3d) {
      const probe = new skinview3d.NameTagObject('x');
      this.Sprite = Object.getPrototypeOf(skinview3d.NameTagObject);
      this.SpriteMaterial = probe.material.constructor;
      this.CanvasTexture = probe.material.map.constructor;
      probe.material.map.dispose();
      probe.material.dispose();
      this.viewer = viewer;
      this.group = new viewer.playerWrapper.constructor();
      // particle space: blocks, with the player's feet at the origin
      this.group.position.y = FEET;
      this.group.scale.setScalar(UNITS);
      viewer.playerWrapper.add(this.group);
      this.textures = {};
      this.pool = [];
      this.used = 0;
      this.emitter = new Emitter();
      this.effect = null;
      this.lastTick = performance.now();
      // update right before skinview3d draws a frame, so sprites and player move together
      const render = viewer.render.bind(viewer);
      viewer.render = () => {
        this.frame();
        render();
      };
    }

    texture(name) {
      if (!this.textures[name]) {
        const tex = new this.CanvasTexture(paint(name));
        tex.colorSpace = 'srgb';
        // like the game's atlas: linear filtering without mipmaps (mipmaps would blend the
        // transparent black around each shape into dark rims)
        tex.generateMipmaps = false;
        tex.minFilter = tex.magFilter = 1006; // THREE.LinearFilter
        this.textures[name] = tex;
      }
      return this.textures[name];
    }

    /** effect: id or '' / null; tint: '#RRGGBB' (Orbit, Sparks); density: the "Amount" setting. */
    set(effect, tint, density) {
      if (effect !== this.effect) this.emitter.particles.length = 0;
      this.effect = effect || null;
      this.emitter.tint = 0xff000000 | parseInt(String(tint || '#B9C6FF').slice(1), 16);
      this.emitter.density = Number(density) || 1;
    }

    sprite(x, y, z, size, width, rot, name, color, alpha) {
      if (alpha <= 0.01 || size <= 0) return;
      let s = this.pool[this.used];
      if (!s) {
        s = new this.Sprite(new this.SpriteMaterial({ transparent: true, depthWrite: false }));
        this.pool.push(s);
        this.group.add(s);
      }
      this.used++;
      const m = s.material;
      if (m.map !== this.texture(name)) {
        m.map = this.texture(name);
        m.needsUpdate = true;
      }
      m.color.setHex(color & 0xffffff);
      m.opacity = (((color >>> 24) & 0xff) / 255) * Math.min(1, alpha);
      m.rotation = rot;
      s.position.set(x, y, z);
      // the game's quad reaches `size` from the center to each side
      s.scale.set(size * 2 * width, size * 2, 1);
      s.visible = true;
    }

    frame() {
      const now = performance.now();
      // the game simulates at 20 ticks per second; catch up at most a second after a pause
      let ticks = Math.floor((now - this.lastTick) / TICK_MS);
      if (ticks > 20) {
        this.lastTick = now - TICK_MS * 20;
        ticks = 20;
      }
      for (let i = 0; i < ticks; i++) {
        this.lastTick += TICK_MS;
        this.emitter.update(0, 0, 0);
        if (this.effect) this.emitter.spawn(this.effect, 0, 0, 0, PREVIEW_HEIGHT);
      }
      const partial = (now - this.lastTick) / TICK_MS;
      this.used = 0;
      const out = this.sprite.bind(this);
      drawParticles(out, this.emitter, partial);
      if (this.effect) drawShapes(out, this.effect, 0, 0, 0, PREVIEW_HEIGHT, this.emitter.tint);
      for (let i = this.used; i < this.pool.length; i++) this.pool[i].visible = false;
    }
  }

  window.LuminaParticles = { Preview, paint };
})();
