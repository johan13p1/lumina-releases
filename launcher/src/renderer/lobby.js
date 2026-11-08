'use strict';

/**
 * The character on the start page, like in a game's lobby: your skin on a stage that you turn by
 * dragging (with momentum, so it can be spun). It breathes and looks around, and every few seconds
 * it plays an emote; a click plays the next one right away. After a while it turns back to the front.
 */
(function () {
  const TAU = Math.PI * 2;
  /** Where the character looks when left alone: a little towards the play button. */
  const FRONT = -0.32;
  const DRAG_SPEED = 0.012; // radians per dragged pixel
  const MAX_SPIN = 30; // radians per second
  const SPIN_FRICTION = 1.7;
  const TURN_BACK_AFTER = 6; // seconds without touching it
  /** Camera: seen a little from above, far away and narrow so the player keeps its proportions. */
  const CAMERA = { fov: 30, y: 8, z: 94, targetY: 2.5 };
  // the player model reaches from -16 (feet) to 16 (top of the head) around its origin
  const FEET_Y = -16;

  const clamp01 = (x) => Math.max(0, Math.min(1, x));
  const smooth = (x) => {
    const t = clamp01(x);
    return t * t * (3 - 2 * t);
  };
  const mix = (a, b, t) => a + (b - a) * t;

  /** Hanging arms and a cape that falls a little off the back; all angles in radians. */
  const REST = {
    headX: 0, headY: 0, headZ: 0,
    rArmX: 0, rArmZ: -0.06, lArmX: 0, lArmZ: 0.06,
    rLegZ: 0, lLegZ: 0,
    y: 0, tilt: 0, lean: 0, twist: 0,
    cape: 0.19,
    // a full turn: not eased in and out, it has to end on a whole turn
    spin: 0,
  };
  const BLENDED = Object.keys(REST).filter((k) => k !== 'spin');

  /** Breathing, a slow look around and shifting the weight from foot to foot. */
  function idle(t) {
    const breath = Math.sin(t * 1.7);
    return {
      ...REST,
      headX: 0.035 * Math.sin(t * 0.53) - 0.015 - 0.008 * breath,
      headY: 0.17 * Math.sin(t * 0.37) + 0.07 * Math.sin(t * 0.83 + 1),
      headZ: 0.02 * Math.sin(t * 0.29),
      rArmX: 0.015 * breath,
      rArmZ: REST.rArmZ - 0.022 * breath,
      lArmX: -0.015 * breath,
      lArmZ: REST.lArmZ + 0.022 * breath,
      lean: 0.012 * Math.sin(t * 0.45),
      y: 0.05 * breath,
      cape: REST.cape + 0.012 * Math.sin(t * 1.3) + 0.006 * Math.sin(t * 3.1),
    };
  }

  /** Each emote changes the idle pose: u runs from 0 to 1, s is the time in seconds. */
  const EMOTES = {
    wave: {
      time: 2.8,
      pose(p, u, s) {
        p.rArmX = -2.75;
        p.rArmZ = -0.45 + 0.42 * Math.sin(s * 10);
        p.headY = -0.2;
        p.headZ = -0.07;
        p.lean = 0.03;
      },
    },
    // a hand over the eyes, looking left and right
    look: {
      time: 3.6,
      pose(p, u) {
        const side = Math.sin(u * TAU);
        p.rArmX = -2.5;
        p.rArmZ = 0.55;
        p.headX = -0.06;
        p.headY = 0.6 * side;
        p.twist = 0.14 * side;
        p.lean = -0.02 * side;
      },
    },
    hop: {
      time: 1.7,
      pose(p, u) {
        const hop = Math.abs(Math.sin(u * TAU));
        p.y = 3.4 * hop;
        p.rArmZ = -0.1 - 1.9 * hop;
        p.lArmZ = 0.1 + 1.9 * hop;
        p.rLegZ = -0.22 * hop;
        p.lLegZ = 0.22 * hop;
        p.headX = 0.08 * hop;
        p.cape = REST.cape + 0.5 * hop;
      },
    },
    stretch: {
      time: 3.2,
      pose(p, u) {
        const side = Math.sin(u * TAU);
        p.rArmX = -3.02;
        p.lArmX = -3.02;
        p.rArmZ = -0.14 + 0.1 * side;
        p.lArmZ = 0.14 + 0.1 * side;
        p.headX = -0.28;
        p.headZ = -0.07 * side;
        p.tilt = -0.05;
        p.lean = 0.07 * side;
      },
    },
    twirl: {
      time: 1.9,
      pose(p, u) {
        const turn = smooth((u - 0.1) / 0.8);
        const open = Math.sin(Math.PI * turn);
        p.spin = TAU * turn;
        p.rArmZ = -0.1 - 1.35 * open;
        p.lArmZ = 0.1 + 1.35 * open;
        p.y = 1.8 * open;
        p.cape = REST.cape + 0.8 * open;
      },
    },
  };

  function shuffled(list) {
    const out = [...list];
    for (let i = out.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [out[i], out[j]] = [out[j], out[i]];
    }
    return out;
  }

  function apply(player, p) {
    const { skin, cape } = player;
    skin.head.rotation.set(p.headX, p.headY, p.headZ);
    skin.rightArm.rotation.set(p.rArmX, 0, p.rArmZ);
    skin.leftArm.rotation.set(p.lArmX, 0, p.lArmZ);
    skin.rightLeg.rotation.set(0, 0, p.rLegZ);
    skin.leftLeg.rotation.set(0, 0, p.lLegZ);
    player.position.set(0, p.y, 0);
    player.rotation.set(p.tilt, p.twist + p.spin, p.lean);
    cape.rotation.x = p.cape;
  }

  /** The pose of every frame: idle, with the running emote eased in and out on top. */
  class Motion {
    constructor() {
      this.time = 0;
      this.emote = null;
      this.bag = [];
      // says hello soon after it appears, then takes its time
      this.wait = 0.9;
      this.next = 'wave';
    }

    play() {
      if (this.emote) return;
      if (!this.next) {
        if (!this.bag.length) {
          this.bag = shuffled(Object.keys(EMOTES));
          // never the same one twice in a row
          if (this.bag[this.bag.length - 1] === this.last) this.bag.reverse();
        }
        this.next = this.bag.pop();
      }
      this.emote = { def: EMOTES[this.next], t: 0 };
      this.last = this.next;
      this.next = null;
    }

    pose(dt) {
      this.time += dt;
      const base = idle(this.time);
      if (!this.emote) {
        this.wait -= dt;
        if (this.wait <= 0) this.play();
        return base;
      }
      const { def } = this.emote;
      this.emote.t += dt;
      const s = this.emote.t;
      if (s >= def.time) {
        this.emote = null;
        this.wait = 3.5 + Math.random() * 4.5;
        return base;
      }
      const target = { ...base };
      def.pose(target, s / def.time, s);
      const edge = Math.min(0.32, def.time / 3);
      const k = smooth(s / edge) * smooth((def.time - s) / edge);
      const out = { ...base, spin: target.spin };
      for (const key of BLENDED) out[key] = mix(base[key], target[key], k);
      return out;
    }
  }

  class Lobby {
    /** root: holds the stage (the canvas goes in here) and gets .ready once the player can be seen. */
    constructor(root, stage, skinview3d, Particles) {
      this.root = root;
      this.stage = stage;
      this.skinKey = null;
      // what the shared cape player of app.js keeps per viewer
      this.capeId = null;
      this.token = 0;
      this.timer = null;

      const canvas = document.createElement('canvas');
      stage.prepend(canvas);
      this.canvas = canvas;
      const viewer = new skinview3d.SkinViewer({ canvas, width: stage.clientWidth || 300, height: stage.clientHeight || 300 });
      this.viewer = viewer;
      viewer.fov = CAMERA.fov;
      viewer.controls.enabled = false;
      viewer.controls.target.set(0, CAMERA.targetY, 0);
      viewer.camera.position.set(0, CAMERA.y, CAMERA.z);
      viewer.playerObject.visible = false;

      this.motion = new Motion();
      this.angle = FRONT;
      this.velocity = 0;
      this.dragging = false;
      this.untouched = 0;
      viewer.animation = this.animation(skinview3d.PlayerAnimation);
      this.particles = new Particles(viewer, skinview3d);

      this.setupDrag();
      this.layout();
      new ResizeObserver(() => this.layout()).observe(stage);
    }

    animation(PlayerAnimation) {
      const lobby = this;
      return new (class extends PlayerAnimation {
        update(player, delta) {
          lobby.frame(player, Math.min(delta, 0.1));
        }
      })();
    }

    frame(player, dt) {
      const pose = this.motion.pose(dt);
      apply(player, pose);
      this.turn(dt);
      // the shadow shrinks when the character leaves the floor
      const lift = Math.max(0, pose.y);
      this.root.style.setProperty('--shadow', (1 - Math.min(0.55, lift * 0.06)).toFixed(3));
    }

    /** Spinning after a drag and, when left alone, the way back to the front (never more than half a turn). */
    turn(dt) {
      if (!this.dragging) {
        this.angle += this.velocity * dt;
        this.velocity *= Math.exp(-SPIN_FRICTION * dt);
        if (Math.abs(this.velocity) < 0.05) {
          this.velocity = 0;
          this.untouched += dt;
        }
        if (this.untouched > TURN_BACK_AFTER) {
          const front = FRONT + Math.round((this.angle - FRONT) / TAU) * TAU;
          this.angle += (front - this.angle) * (1 - Math.exp(-2.4 * dt));
          if (Math.abs(front - this.angle) < 0.001) this.angle = FRONT;
        }
      }
      this.viewer.playerWrapper.rotation.y = this.angle;
    }

    setupDrag() {
      const { canvas, stage } = this;
      let lastX = 0;
      let lastTime = 0;
      let moved = 0;
      let start = 0;
      canvas.addEventListener('pointerdown', (e) => {
        if (e.button !== 0) return;
        canvas.setPointerCapture(e.pointerId);
        this.dragging = true;
        this.velocity = 0;
        this.untouched = 0;
        lastX = e.clientX;
        lastTime = start = e.timeStamp;
        moved = 0;
        stage.classList.add('dragging');
      });
      canvas.addEventListener('pointermove', (e) => {
        if (!this.dragging) return;
        const dx = e.clientX - lastX;
        const seconds = Math.max(e.timeStamp - lastTime, 1) / 1000;
        this.angle += dx * DRAG_SPEED;
        this.velocity = mix(this.velocity, (dx * DRAG_SPEED) / seconds, 0.35);
        lastX = e.clientX;
        lastTime = e.timeStamp;
        moved += Math.abs(dx);
      });
      const release = (e) => {
        if (!this.dragging) return;
        this.dragging = false;
        this.untouched = 0;
        stage.classList.remove('dragging');
        // held still before letting go: no spin
        if (e.timeStamp - lastTime > 80) this.velocity = 0;
        this.velocity = Math.max(-MAX_SPIN, Math.min(MAX_SPIN, this.velocity));
        if (e.type === 'pointerup' && moved < 4 && e.timeStamp - start < 350) this.motion.play();
      };
      canvas.addEventListener('pointerup', release);
      canvas.addEventListener('pointercancel', release);
    }

    /** Fits the canvas to the stage and tells the stylesheet where feet and head are on screen. */
    layout() {
      const { viewer, stage } = this;
      const w = stage.clientWidth;
      const h = stage.clientHeight;
      if (!w || !h) return;
      viewer.setSize(w, h);
      viewer.controls.update();
      viewer.camera.updateMatrixWorld();
      const screenY = (y) => {
        const v = viewer.playerObject.position.clone().set(0, y, 0).project(viewer.camera);
        return ((1 - v.y) / 2) * h;
      };
      const feet = screenY(FEET_Y);
      this.pixelsPerUnit = (feet - screenY(FEET_Y + 32)) / 32;
      this.root.style.setProperty('--feet', `${feet.toFixed(1)}px`);
      this.root.style.setProperty('--unit', `${this.pixelsPerUnit.toFixed(2)}px`);
      if (viewer.renderPaused) viewer.render();
    }

    /** skin: { url, data, slim }. */
    async setSkin(skin) {
      const key = `${skin.url}|${skin.slim}`;
      if (key === this.skinKey) return;
      this.skinKey = key;
      await this.viewer.loadSkin(skin.data, { model: skin.slim ? 'slim' : 'default' });
      this.viewer.playerObject.visible = true;
      this.root.classList.add('ready');
    }

    setPaused(paused) {
      if (this.viewer.renderPaused !== paused) this.viewer.renderPaused = paused;
    }
  }

  window.LuminaLobby = { Lobby };
})();
