/**
 * Echter Test von MorniSMP mit zwei Test-Spielern (mineflayer) auf Paper 26.1.2 – gestartet von test/run.sh.
 *   node smp-test.cjs <host> <port> <rcon-host:port> <rcon-passwort-datei> <server-ordner>
 *
 * Anna und Bert (offline-Konten) gehen durch: Marktstadt, Geld, Überweisung, Auktionshaus (verkaufen + kaufen mit
 * Gebühr), Auftrag (aufgeben, beliefern, abholen), Ankauf, Marktstand mieten, Zufalls-Teleport, Grundstück und
 * Schutz, Zuhause, Kopfgeld, Belohnung, Leiste.
 *
 * Verfasst mit Claude 🤖
 */
const fs = require('node:fs');
const mineflayer = require('mineflayer');
const { rcon } = require('./rcon.cjs');

const path = require('node:path');

const [HOST = '127.0.0.1', PORT = '25740', RCON = '127.0.0.1:25840', PASS_FILE, SERVER] = process.argv.slice(2);
const NETWORK = process.env.NO_NETWORK !== '1';
const PASS = fs.readFileSync(PASS_FILE, 'utf8').trim();
const VERSION = process.env.MC_VERSION ?? '26.1';

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function check(name, ok, detail = '') {
  results.push({ name, ok: Boolean(ok) });
  console.log(`${ok ? '✓' : '✗'} ${name}${detail ? ` – ${detail}` : ''}`);
}

/** Text aus Chat-Komponenten (String, JSON oder NBT) zusammensetzen. */
function plain(v) {
  if (v == null) return '';
  if (typeof v === 'string') {
    if (v.startsWith('{') || v.startsWith('[')) {
      try {
        return plain(JSON.parse(v));
      } catch {
        return v;
      }
    }
    return v;
  }
  if (Array.isArray(v)) return v.map(plain).join('');
  if (typeof v !== 'object') return String(v);
  if (v.type === 'compound') return plain(v.value);
  if (v.type === 'list') return plain(v.value?.value ?? []);
  if (v.type === 'string') return v.value;
  let s = '';
  if ('text' in v) s += plain(v.text);
  if ('' in v) s += plain(v['']);
  if ('translate' in v) s += plain(v.translate);
  if ('extra' in v) s += plain(v.extra);
  return s;
}

const cmd = (...commands) => rcon(RCON, PASS, commands);

/** In welcher Welt ist der Spieler (laut Server)? Schlüssel wie „minecraft:smp_spawn“, „minecraft:the_nether“. */
async function inDim(name, dim) {
  const out = await cmd(`execute as ${name} at @s if dimension ${dim}`);
  return /passed/i.test(out.join(' '));
}

/** Fester, sicherer Boden unter den Füßen und Platz für Füße und Kopf (Sicht des Test-Spielers). */
async function safeGround(bot) {
  const p = bot.entity.position;
  await waitFor(() => bot.blockAt(p.offset(0, -0.5, 0)) != null && bot.blockAt(p.offset(0, 1, 0)) != null, 10000);
  // Seelensand & Co. sind niedriger als ein Block: die Füße stehen dann noch im Bodenblock
  const ground = bot.blockAt(p.offset(0, -0.2, 0));
  const feet = bot.blockAt(p.offset(0, 0.5, 0));
  const head = bot.blockAt(p.offset(0, 1.5, 0));
  const ok = ground && ground.boundingBox === 'block' && !/water|lava|magma|fire/.test(ground.name) &&
    feet && feet.boundingBox === 'empty' && !/lava|fire/.test(feet.name) && head && head.boundingBox === 'empty';
  return { ok: Boolean(ok), detail: `${ground?.name}/${feet?.name}/${head?.name} y=${p.y.toFixed(1)}` };
}

/** Steht der Block wirklich noch (Server fragen, nicht die Sicht des Test-Spielers)? */
async function serverHas(pos, name, dim = 'minecraft:overworld') {
  const out = await cmd(`execute in ${dim} if block ${pos.x} ${pos.y} ${pos.z} minecraft:${name}`);
  return /passed/i.test(out.join(' '));
}

async function waitFor(cond, ms = 5000, step = 100) {
  const start = Date.now();
  while (Date.now() - start < ms) {
    if (await cond()) return true;
    await sleep(step);
  }
  return Boolean(await cond());
}

let lastJoin = 0;
async function join(name) {
  const wait = lastJoin + 1500 - Date.now();
  if (wait > 0) await sleep(wait);
  lastJoin = Date.now();
  const bot = mineflayer.createBot({ host: HOST, port: Number(PORT), username: name, version: VERSION, auth: 'offline' });
  bot.chats = [];
  bot.bars = [];
  bot.titles = [];
  bot.on('messagestr', (m, position) => (position === 'game_info' ? bot.bars : bot.chats).push(m));
  bot._client.on('action_bar', (p) => bot.bars.push(plain(p.text)));
  bot._client.on('set_title_text', (p) => bot.titles.push(plain(p.text)));
  bot.on('kicked', (r) => console.log(`  (${name} gekickt: ${plain(r)})`));
  bot.on('error', (e) => console.log(`  (${name}: ${e.message})`));
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve);
    setTimeout(() => reject(new Error(`${name}: kein Spawn`)), 30000);
  });
  await sleep(2500);
  return bot;
}

const sees = (bot, re) => bot.chats.some((c) => re.test(c));
const last = (bot, re) => [...bot.chats].reverse().find((c) => re.test(c)) ?? '';
const clear = (...bots) => bots.forEach((b) => { b.chats.length = 0; b.bars.length = 0; });

/** Befehl schicken und auf eine passende Chat-Zeile warten. */
async function say(bot, text, re, ms = 6000) {
  bot.chats.length = 0;
  bot.chat(text);
  const ok = await waitFor(() => sees(bot, re), ms);
  return ok ? last(bot, re) : '';
}

/** „1.234,50 ₮“ → 1234.5 */
function taler(s) {
  const m = /([\d.]+(?:,\d{1,2})?) ₮/.exec(s);
  return m ? Number(m[1].replace(/\./g, '').replace(',', '.')) : NaN;
}

async function balance(bot) {
  return taler(await say(bot, '/geld', /Kontostand: [\d.,]+ ₮/));
}

function nextWindow(bot, ms = 5000) {
  return new Promise((resolve) => {
    const t = setTimeout(() => resolve(null), ms);
    bot.once('windowOpen', (w) => {
      clearTimeout(t);
      resolve(w);
    });
  });
}

async function openBy(bot, text) {
  const w = nextWindow(bot);
  bot.chat(text);
  return w;
}

async function click(bot, slot, mode = 0) {
  await bot.clickWindow(slot, 0, mode);
  await sleep(400);
}

const count = (bot, name) => bot.inventory.items().filter((i) => i.name === name).reduce((n, i) => n + i.count, 0);

async function hold(bot, name) {
  const it = bot.inventory.items().find((i) => i.name === name);
  if (!it) return false;
  await bot.equip(it, 'hand');
  await sleep(300);
  return true;
}

function title(w) {
  return plain(w?.title);
}

/** Name + Zeilen eines Gegenstands im Menü als Text. */
function itemText(it) {
  if (!it) return '';
  const lore = it.customLore;
  return `${plain(it.customName)} | ${Array.isArray(lore) ? lore.map(plain).join(' | ') : plain(lore)}`;
}

/** Klick auf einen Menü-Platz, der ein neues Menü öffnet. */
async function clickOpen(bot, slot, mode = 0) {
  const w = nextWindow(bot);
  await bot.clickWindow(slot, 0, mode);
  return w;
}

/** Platz eines Gegenstands (Sorte) oben im Menü. */
function slotOf(w, name) {
  return w?.slots.findIndex((it, i) => i >= 9 && i < w.inventoryStart && it?.name === name) ?? -1;
}

/** Warten, bis ein Teleport mit Wartezeit durch ist (Position springt). */
async function teleported(bot, from, ms = 12000) {
  return waitFor(() => bot.entity.position.distanceTo(from) > 8, ms, 200);
}

async function main() {
  await cmd('difficulty peaceful', 'time set day', 'gamerule doDaylightCycle false', 'weather clear');
  const anna = await join('Anna');
  const bert = await join('Bert');

  // ------------------------------------------------------------------ Marktstadt
  const pos = anna.entity.position.clone();
  check('Ankunft in der Marktstadt (bei 0/0)', Math.abs(pos.x) < 58 && Math.abs(pos.z) < 58, pos.toString());
  let solid = 0;
  const kinds = new Set();
  for (let dx = -20; dx <= 20; dx++) {
    for (let dz = -20; dz <= 20; dz++) {
      for (let dy = -1; dy <= 8; dy++) {
        const b = anna.blockAt(pos.offset(dx, dy, dz));
        if (b && b.name !== 'air') {
          solid++;
          kinds.add(b.name);
        }
      }
    }
  }
  check('Stadt steht (viele verschiedene Blöcke um die Ankunft)', solid > 1500 && kinds.size > 15,
    `${solid} Blöcke, ${kinds.size} Sorten`);
  const board = Object.values(anna.entities).find((e) => e.name === 'text_display');
  check('Bestenliste (schwebende Schrift) am Brunnen', Boolean(board));
  check('Begrüßung beim ersten Betreten', sees(anna, /Schön, dass du da bist/) || anna.titles.some((t) => /MorniSMP/.test(t)));
  const sidebar = Object.values(anna.scoreboards ?? {}).find((s) => /MorniSMP/.test(plain(s.title)));
  check('Leiste rechts mit MorniSMP', Boolean(sidebar));

  // ------------------------------------------------------------------ Händlerhaus: Figur und Schaustücke
  await waitFor(async () => /count: 1\b|passed/i.test((await cmd('execute in minecraft:smp_spawn if entity '
    + '@e[type=villager,tag=mornismp_merchant]')).join(' ')), 15000, 1000);
  const npcCount = (await cmd('execute in minecraft:smp_spawn if entity @e[type=villager,tag=mornismp_merchant]')).join(' ');
  check('Händlerin steht im Händlerhaus (genau eine)', /count: 1\b/i.test(npcCount), npcCount);
  const shows = (await cmd('execute in minecraft:smp_spawn if entity @e[type=item_display,tag=mornismp_showcase]')).join(' ');
  check('Zwei Schaustücke über dem Tresen', /count: 2\b/i.test(shows), shows);
  const logText = fs.readFileSync(path.join(SERVER, 'console.log'), 'utf8');
  check('Sortiment geladen, keine unbekannten Sorten', /Händlerhaus: 2\d\d Waren in 10 Abteilungen/.test(logText)
    && !/Händlerhaus: Unbekannte Sorte/.test(logText), (/Händlerhaus: [^\n]*/.exec(logText) ?? [''])[0]);
  const npc = Object.values(anna.entities).find((e) => e.name === 'villager');
  if (npc) {
    await cmd(`execute in minecraft:smp_spawn run tp Anna ${npc.position.x - 3} ${npc.position.y} ${npc.position.z}`);
    await sleep(1200);
    await anna.lookAt(npc.position.offset(0, 1.5, 0), true);
    const npcWin = nextWindow(anna);
    await anna.activateEntity(npc);
    const nw = await npcWin;
    check('Rechtsklick auf die Händlerin öffnet das Händlerhaus (kein Handel)', /Händlerhaus/.test(title(nw)), title(nw));
    if (anna.currentWindow) anna.closeWindow(anna.currentWindow);
  } else {
    check('Rechtsklick auf die Händlerin öffnet das Händlerhaus (kein Handel)', false, 'keine Figur gesehen');
  }
  await cmd('execute in minecraft:smp_spawn run tp Anna 0.5 ' + Math.floor(pos.y) + ' 10.5');
  await sleep(800);

  // ------------------------------------------------------------------ Spawn-Welt und Reisetore
  check('Neue Spieler landen in der Spawn-Welt (smp_spawn)', await inDim('Anna', 'minecraft:smp_spawn'));
  const below = anna.blockAt(pos.offset(0, -6, 0));
  check('Stadt schwebt als Insel (unter dem Platz Fels, ganz unten Leere)', below && below.name !== 'air' &&
    (anna.blockAt(pos.offset(0, -60, 0))?.name ?? 'air') === 'air', below?.name);
  const claimSpawn = await say(anna, '/gs sichern', /Spawn-Welt/);
  check('Keine Grundstücke in der Spawn-Welt', claimSpawn !== '', claimSpawn);
  const gateLines = (await cmd('smpadmin stadt tore')).join('\n');
  const gates = {};
  for (const m of gateLines.matchAll(/(overworld|nether|end)(?:§.)*: Tor (?:§.)*([-\d.]+) ([-\d.]+) ([-\d.]+)(?:§.)*, davor (?:§.)*([-\d.]+) ([-\d.]+) ([-\d.]+)/g)) {
    gates[m[1]] = { gate: m.slice(2, 5).map(Number), back: m.slice(5, 8).map(Number) };
  }
  check('Drei Reisetore (Oberwelt, Nether, End)', Object.keys(gates).length === 3, Object.keys(gates).join(','));
  const labels = Object.values(anna.entities).filter((e) => e.name === 'text_display').length;
  const membranes = Object.values(anna.entities).filter((e) => e.name === 'block_display').length;
  check('Tore haben Beschriftung und schimmernde Fläche', labels >= 4 && membranes >= 3, `${labels} Schriften, ${membranes} Flächen`);
  for (const target of ['overworld', 'nether', 'end']) {
    const out = (await cmd(`execute in minecraft:smp_spawn if entity @e[type=text_display,tag=morni_smp_gate_${target}]`))
      .join(' ');
    check(`Reisetor ${target}: große Ziel-Schrift + „Reisetor“-Zeile`, /count: 2\b/i.test(out), out);
    // Schrift steht frei (Luft), nicht in der Wand, und ist für den Client groß genug (kein Punkt → kein Wegschneiden)
    const sel = `@e[type=text_display,tag=morni_smp_gate_${target}]`;
    const free = (await cmd(`execute in minecraft:smp_spawn as ${sel} at @s if block ~ ~ ~ minecraft:air`)).join(' ');
    const size = (await cmd(`execute in minecraft:smp_spawn as ${sel} at @s if entity @s[nbt={width:14.0f,height:3.0f}]`))
      .join(' ');
    const where = (await cmd(`execute in minecraft:smp_spawn as ${sel} run data get entity @s Pos`)).join(' ');
    check(`Reisetor ${target}: Schrift frei in der Luft, Größe gesetzt`, (free.match(/passed/gi) ?? []).length === 2 && (size.match(/passed/gi) ?? []).length === 2,
      where.replace(/Text Display has the following entity data: /g, ''));
  }
  const fountain = anna.findBlocks({ matching: (b) => b.name === 'quartz_pillar' || b.name === 'gold_block', maxDistance: 16,
    count: 30 }).length;
  check('Marmor-Brunnen mit Gold auf dem Marktplatz', fountain >= 8, `${fountain} Quarzsäulen/Goldblöcke`);
  // Brunnen: nur stilles Wasser in Becken und Schale, nichts Fließendes (auch nach dem Neubau einer alten Fassung)
  const GROUND = Math.floor(pos.y) - 1;
  const fountainWater = () => {
    let still = 0;
    const flowing = [];
    for (let x = -8; x <= 8; x++) {
      for (let z = -8; z <= 8; z++) {
        for (let y = GROUND; y <= GROUND + 12; y++) {
          const b = anna.blockAt(new (pos.constructor)(x, y, z));
          if (b?.name === 'water') {
            const level = Number(b.getProperties?.().level ?? 0);
            const d2 = x * x + z * z;
            const inside = (y === GROUND + 1 && d2 <= 13) || (y === GROUND + 6 && d2 >= 1 && d2 <= 2);
            if (level === 0 && inside) still++;
            else flowing.push(`${x},${y},${z}:${level}`);
          }
        }
      }
    }
    return { still, flowing };
  };
  await sleep(3000);
  let fw = fountainWater();
  check('Brunnen (frische Welt): nur stilles Wasser in Becken und Schale', fw.flowing.length === 0 && fw.still >= 40,
    `${fw.still} still, fließend: ${fw.flowing.slice(0, 6).join(' ')}`);
  // alte Fassung nachstellen: Wasser außerhalb der Schalen, das fließt – dann /smpadmin stadt neu
  const log = path.join(SERVER, 'console.log');
  const built = () => (fs.readFileSync(log, 'utf8').match(/Marktstadt fertig/g) ?? []).length;
  const builtBefore = built();
  await cmd(`execute in minecraft:smp_spawn run setblock 1 ${GROUND + 8} 0 minecraft:water`,
    `execute in minecraft:smp_spawn run setblock 3 ${GROUND + 5} 0 minecraft:water`);
  await sleep(4000);
  fw = fountainWater();
  check('Nachgestellt: fließendes Wasser einer alten Fassung', fw.flowing.length > 3, String(fw.flowing.length));
  await cmd('smpadmin stadt neu');
  await waitFor(() => built() > builtBefore, 120000, 1000);
  await sleep(6000); // ein paar Sekunden ticken lassen
  fw = fountainWater();
  check('Nach dem Neubau: altes Wasser weg, nur stilles Wasser in den Schalen', built() > builtBefore &&
    fw.flowing.length === 0 && fw.still >= 40, `${fw.still} still, fließend: ${fw.flowing.slice(0, 6).join(' ')}`);
  await waitFor(async () => /count: 2/i.test((await cmd(
    'execute in minecraft:smp_spawn if entity @e[type=text_display,tag=morni_smp_gate_end]')).join(' ')), 15000, 1000);

  const DIMS = { overworld: 'minecraft:overworld', nether: 'minecraft:the_nether', end: 'minecraft:the_end' };
  for (const target of ['overworld', 'nether', 'end']) {
    const g = gates[target];
    if (!g) {
      check(`Reisetor ${target}`, false, 'nicht gefunden');
      continue;
    }
    await cmd(`execute in minecraft:smp_spawn run tp Anna ${g.back[0]} ${g.back[1]} ${g.back[2]}`);
    await sleep(1500);
    await anna.lookAt(anna.entity.position.offset(g.gate[0] - g.back[0], 1.6, g.gate[2] - g.back[2]), true);
    clear(anna);
    const startPos = anna.entity.position.clone();
    anna.setControlState('forward', true);
    // stehen bleiben, sobald es weggeht (zurückgeschoben = höchstens ein paar Blöcke, Teleport = weit weg)
    await waitFor(() => anna.entity.position.distanceTo(startPos) > 40, 25000, 50);
    anna.setControlState('forward', false);
    const left = await waitFor(async () => !(await inDim('Anna', 'minecraft:smp_spawn')), 5000, 500);
    const there = left && await inDim('Anna', DIMS[target]);
    check(`Reisetor ${target}: hineingehen → richtige Dimension`, there, anna.entity.position.toString());
    await sleep(2500);
    const sg = await safeGround(anna);
    check(`Reisetor ${target}: sicherer Boden`, sg.ok, sg.detail);
    if (target === 'nether') {
      const y = anna.entity.position.y;
      check('Nether: zwischen y 32 und 111 (unter dem Dach)', y >= 32 && y <= 112, y.toFixed(1));
    }
    if (target === 'end') {
      check('End: nicht in der Leere', anna.entity.position.y > 20, anna.entity.position.y.toFixed(1));
    }
    const pre = anna.entity.position.clone();
    anna.chat('/spawn');
    await waitFor(async () => await inDim('Anna', 'minecraft:smp_spawn'), 15000, 500);
    check(`/spawn bringt aus ${target} zurück in die Spawn-Welt`, await inDim('Anna', 'minecraft:smp_spawn'), pre.toString());
    await sleep(6000); // Abklingzeit im Test: 5 s
  }
  for (const target of ['nether', 'end']) {
    clear(anna);
    anna.chat(`/rtp ${target}`);
    const ok = await waitFor(async () => await inDim('Anna', DIMS[target]), 40000, 500);
    await waitFor(() => sees(anna, /Du bist bei/), 5000);
    check(`/rtp ${target}: Suche + Wartezeit → ${target}`, ok && sees(anna, /sicheren Platz im/), anna.entity.position.toString());
    await sleep(2500);
    const sg = await safeGround(anna);
    check(`/rtp ${target}: sicherer Boden`, sg.ok, sg.detail);
    anna.chat('/spawn');
    await waitFor(async () => await inDim('Anna', 'minecraft:smp_spawn'), 15000, 500);
    await sleep(6000);
  }
  check('Wieder in der Spawn-Welt', await inDim('Anna', 'minecraft:smp_spawn'));
  await cmd('execute in minecraft:smp_spawn run tp Anna ' + `${pos.x} ${pos.y} ${pos.z}`);
  await sleep(1500);

  // ------------------------------------------------------------------ Geld
  const start = await balance(anna);
  check('/geld: Startguthaben 100 ₮', start === 100, String(start));
  await cmd('smpadmin geld geben Anna 2000', 'smpadmin geld geben Bert 2000');
  check('/smpadmin geld geben (Konsole)', (await balance(anna)) === 2100 && (await balance(bert)) === 2100);
  clear(bert);
  const paid = await say(anna, '/zahlen Bert 50', /überwiesen/);
  check('/zahlen Bert 50', /50 ₮ an Bert überwiesen/.test(paid), paid);
  await waitFor(() => sees(bert, /Anna hat dir 50 ₮ überwiesen/), 3000);
  check('Bert bekommt Bescheid', sees(bert, /Anna hat dir 50 ₮ überwiesen/));
  check('Kontostände nach /zahlen: 2050 / 2150', (await balance(anna)) === 2050 && (await balance(bert)) === 2150);
  const broke = await say(anna, '/zahlen Bert 999999', /reicht dein Geld nicht|Wirklich/);
  check('Zu wenig Geld wird abgelehnt (oder erst nachgefragt)', broke !== '' || anna.currentWindow != null);
  if (anna.currentWindow) anna.closeWindow(anna.currentWindow);
  const top = await say(anna, '/reichste', /Die Reichsten/);
  check('/reichste', top !== '');

  // ------------------------------------------------------------------ Auktionshaus
  await cmd('clear Anna', 'clear Bert', 'give Anna diamond 16');
  await sleep(800);
  await hold(anna, 'diamond');
  const listed = await say(anna, '/ah verkaufen 200', /Angeboten|Angebote/);
  check('/ah verkaufen 200 (16 Diamanten aus der Hand)', /Angeboten: 16× .*200 ₮/.test(listed), listed);
  check('Diamanten sind aus dem Inventar weg', count(anna, 'diamond') === 0);
  const ah = await openBy(bert, '/ah');
  check('Bert öffnet das Auktionshaus', /Auktionshaus/.test(title(ah)), title(ah));
  const listing = ah?.slots[9];
  check('Angebot liegt im ersten Platz (Diamant)', listing?.name === 'diamond' && listing.count === 16, listing?.name);
  let confirm = nextWindow(bert);
  await click(bert, 9);
  const cw = await confirm;
  check('Kauf-Nachfrage öffnet sich', /Wirklich kaufen/.test(title(cw)), title(cw));
  clear(anna, bert);
  await click(bert, 10);
  await waitFor(() => sees(bert, /Gekauft/), 4000);
  check('Bert hat gekauft (Nachricht)', sees(bert, /Gekauft: 16× .*200 ₮ von Anna/), last(bert, /Gekauft/));
  await waitFor(() => count(bert, 'diamond') === 16, 3000);
  check('Bert hat 16 Diamanten im Inventar', count(bert, 'diamond') === 16, String(count(bert, 'diamond')));
  await waitFor(() => sees(anna, /Verkauft!/), 3000);
  check('Anna bekommt „Verkauft!“ mit Gebühr', sees(anna, /Verkauft!.*Bert.*200 ₮.*Gebühr 4 ₮/), last(anna, /Verkauft/));
  check('Geld: Anna +196 (2 % Gebühr), Bert −200', (await balance(anna)) === 2246 && (await balance(bert)) === 1950);

  // ------------------------------------------------------------------ Aufträge
  await cmd('give Bert wheat 1', 'give Anna wheat 10');
  await sleep(800);
  await hold(bert, 'wheat');
  confirm = nextWindow(bert);
  bert.chat('/auftraege neu 10 5');
  const ow = await confirm;
  check('Auftrag: Nachfrage mit Summe', /Auftrag aufgeben/.test(title(ow)), title(ow));
  clear(bert);
  await click(bert, 10);
  await waitFor(() => sees(bert, /Auftrag steht/), 4000);
  check('Auftrag steht (10 Weizen zu 5 ₮)', sees(bert, /Auftrag steht: 10× .*5 ₮/), last(bert, /Auftrag/));
  check('50 ₮ liegen in der Treuhand (Bert 1900)', (await balance(bert)) === 1900);
  const board2 = await openBy(anna, '/auftraege');
  check('Anna öffnet das Auftragsbrett', /Auftragsbrett/.test(title(board2)), title(board2));
  check('Auftrag liegt im ersten Platz (Weizen)', board2?.slots[9]?.name === 'wheat', board2?.slots[9]?.name);
  const dw = nextWindow(anna);
  await click(anna, 9);
  const deliver = await dw;
  check('Liefern-Menü', /Liefern/.test(title(deliver)), title(deliver));
  clear(anna, bert);
  await click(anna, 11);
  await waitFor(() => sees(anna, /geliefert/), 4000);
  check('Anna liefert 10 Weizen (+49 ₮, 1 ₮ Gebühr)', sees(anna, /10× .*an Bert geliefert: \+49 ₮/), last(anna, /geliefert/));
  check('Weizen ist bei Anna weg', count(anna, 'wheat') === 0);
  check('Anna hat jetzt 2295 ₮', (await balance(anna)) === 2295);
  await waitFor(() => sees(bert, /Auftrag erfüllt/), 3000);
  check('Bert: „Auftrag erfüllt!“', sees(bert, /Auftrag erfüllt/), last(bert, /Auftrag/));
  const mail = await openBy(bert, '/abholen');
  check('Abholfach öffnet sich', /Abholfach/.test(title(mail)), title(mail));
  clear(bert);
  const before = count(bert, 'wheat');
  await click(bert, 49);
  await waitFor(() => count(bert, 'wheat') >= before + 10, 3000);
  check('Bert holt die 10 Weizen ab', count(bert, 'wheat') === before + 10, `${before} → ${count(bert, 'wheat')}`);
  if (bert.currentWindow) bert.closeWindow(bert.currentWindow);

  // ------------------------------------------------------------------ Ankauf
  await cmd('give Anna wheat 64', 'give Anna torch 1');
  await sleep(800);
  const bw = await openBy(anna, '/verkaufen');
  check('Ankauf öffnet sich', /Ankauf/.test(title(bw)), title(bw));
  for (const name of ['wheat', 'torch']) {
    const it = bw?.findInventoryItem(name, null, false);
    if (it) await click(anna, it.slot, 1);
  }
  await sleep(600);
  const btn = plain(anna.currentWindow?.slots[49]?.customName);
  check('Knopf zeigt den Erlös (64 Weizen = 32 ₮)', /32 ₮/.test(btn), btn);
  clear(anna);
  await click(anna, 49);
  await waitFor(() => sees(anna, /an die Händlerin verkauft/), 3000);
  check('Ankauf: +32 ₮ (Weizen)', sees(anna, /an die Händlerin verkauft: \+32 ₮/), last(anna, /verkauft/));
  if (anna.currentWindow) anna.closeWindow(anna.currentWindow);
  await sleep(800);
  check('Fackel (kauft die Händlerin nicht an) kommt zurück', count(anna, 'torch') === 1, String(count(anna, 'torch')));
  check('Anna hat 2327 ₮', (await balance(anna)) === 2327);
  await cmd('give Anna wheat 300');
  await sleep(800);
  const bw2 = await openBy(anna, '/verkaufen');
  for (const it of bw2 ? bw2.items().filter((i) => i.name === 'wheat' && i.slot >= bw2.inventoryStart) : []) {
    await bot_shift(anna, it.slot);
  }
  await sleep(600);
  const btn2 = plain(anna.currentWindow?.slots[49]?.customName);
  const value = taler(btn2);
  // heute schon 64: noch 192 × 0,50 = 96, danach 108 × 0,25 (50 %) = 27 → 123 statt 150
  check('Sinkende Preise: 300 Weizen bringen 123 ₮ statt 150 ₮', value === 123, btn2);
  await click(anna, 49);
  if (anna.currentWindow) anna.closeWindow(anna.currentWindow);
  await sleep(500);

  // ------------------------------------------------------------------ Marktstand (über das Schild am Stand)
  const signText = (b) => plain(b?.blockEntity?.front_text?.messages?.[0]);
  const signPos = anna.findBlocks({ matching: (b) => /hanging_sign/.test(b.name), maxDistance: 40, count: 40 })
    .find((p) => /^Stand \d+$/.test(signText(anna.blockAt(p))));
  const sign = signPos ? anna.blockAt(signPos) : null;
  check('Hängeschild eines Marktstands gefunden', Boolean(sign), signText(sign));
  if (sign) {
    await cmd(`execute in minecraft:smp_spawn run tp Anna ${sign.position.x + 0.5} ${sign.position.y - 2} ${sign.position.z + 0.5}`);
    await sleep(1200);
    const sw = nextWindow(anna);
    await anna.activateBlock(anna.blockAt(sign.position));
    const stall = await sw;
    check('Klick aufs Standschild öffnet den Stand', /Marktstand \d+/.test(title(stall)), title(stall));
    if (stall) {
      clear(anna);
      await click(anna, 13);
      await waitFor(() => sees(anna, /gehört dir/), 3000);
      check('Stand gemietet (300 ₮ pro Woche)', sees(anna, /Stand \d+ gehört dir/), last(anna, /Stand/));
    }
  }
  const afterStall = await balance(anna);

  // ------------------------------------------------------------------ Kopfgeld + Belohnung
  clear(bert);
  const bounty = await say(anna, '/kopfgeld Bert 100', /Kopfgeld auf Bert/);
  check('/kopfgeld Bert 100 (alle sehen es)', bounty !== '' && (await waitFor(() => sees(bert, /Kopfgeld auf Bert/), 2000)));
  const reward = await say(bert, '/belohnung', /Belohnung/);
  check('/belohnung: 25 ₮ am ersten Tag', /Tägliche Belohnung: 25 ₮/.test(reward), reward);
  const again = await say(bert, '/belohnung', /schon/);
  check('Belohnung nur einmal am Tag', again !== '');

  // ------------------------------------------------------------------ Schutz in der Stadt
  await cmd('gamemode creative Bert');
  await sleep(500);
  const tpos = bert.entity.position.floored();
  let target = null;
  for (const [dx, dz] of [[1, 0], [0, 1], [-1, 0], [0, -1], [2, 0]]) {
    const b = bert.blockAt(tpos.offset(dx, -1, dz));
    if (b && b.name !== 'air' && b.diggable) {
      target = b;
      break;
    }
  }
  if (target) {
    const name = target.name;
    try {
      await bert.dig(target, true);
    } catch {
      // abgebrochen ist auch gut
    }
    await sleep(1000);
    check('Stadt ist geschützt (Block bleibt, laut Server)', await serverHas(target.position, name, 'minecraft:smp_spawn'),
      name);
  } else {
    check('Stadt ist geschützt (Block bleibt)', false, 'kein Block gefunden');
  }
  await cmd('gamemode survival Bert');

  // ------------------------------------------------------------------ Zufalls-Teleport
  const p0 = anna.entity.position.clone();
  clear(anna);
  anna.chat('/rtp nah');
  const moved = await teleported(anna, p0, 30000);
  await waitFor(() => sees(anna, /Du bist bei/), 3000);
  const p1 = anna.entity.position.clone();
  const dist = Math.hypot(p1.x, p1.z);
  check('/rtp nah: Suche + 3 s Wartezeit + Teleport', moved && sees(anna, /sicheren Platz/), p1.toString());
  check('Ziel liegt im Ring 500–2000 in der Oberwelt', dist >= 480 && dist <= 2020 &&
    await inDim('Anna', 'minecraft:overworld'), `Abstand ${dist.toFixed(0)}`);
  await waitFor(() => anna.blockAt(p1.offset(0, -0.5, 0)) != null, 10000);
  const ground = anna.blockAt(p1.offset(0, -0.5, 0));
  const feet = anna.blockAt(p1);
  check('Sicherer Platz: fester Boden, Füße frei, kein Wasser', ground && ground.boundingBox === 'block' &&
    !/water|lava/.test(ground.name) && feet && feet.boundingBox === 'empty', `${ground?.name} / ${feet?.name}`);
  check('Abklingzeit danach', (await say(anna, '/rtp nah', /warten|wieder möglich/)) !== '');

  // ------------------------------------------------------------------ Grundstück
  const claimed = await say(anna, '/gs sichern', /Gesichert|gehört|nah an der Marktstadt/);
  check('/gs sichern (gratis)', /Gesichert! Das ist dein Grundstück Nr\. 1/.test(claimed), claimed);
  await cmd('tp Bert Anna', 'gamemode creative Bert');
  await sleep(2500);
  const bp = bert.entity.position.floored();
  // ein Block im selben Chunk wie Annas Grundstück (am Chunk-Rand wäre der Nachbar schon Wildnis)
  const claimChunk = `${Math.floor(anna.entity.position.x / 16)}:${Math.floor(anna.entity.position.z / 16)}`;
  let victim = null;
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1], [0, 0]]) {
    const b = bert.blockAt(bp.offset(dx, -1, dz));
    if (b && b.name !== 'air' && !/water|lava/.test(b.name) &&
      `${Math.floor(b.position.x / 16)}:${Math.floor(b.position.z / 16)}` === claimChunk) {
      victim = b;
      break;
    }
  }
  if (victim) {
    const name = victim.name;
    clear(bert);
    try {
      await bert.dig(victim, true);
    } catch {
      // abgebrochen
    }
    await sleep(1000);
    check('Grundstück geschützt: Bert kann nicht abbauen (laut Server)', await serverHas(victim.position, name), name);
    check('Hinweis „gehört Anna“', bert.bars.some((b) => /gehört Anna/.test(b)), bert.bars.join(' | '));
    await say(anna, '/gs vertrauen Bert', /darf jetzt/);
    await sleep(300);
    // ein anderer Block im selben Chunk (die Sicht des Test-Spielers auf den ersten ist evtl. noch „Luft“)
    const chunk = (v) => `${Math.floor(v.x / 16)}:${Math.floor(v.z / 16)}`;
    let second = null;
    for (const [dx, dz] of [[-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]]) {
      const b = bert.blockAt(bp.offset(dx, -1, dz));
      if (b && b.name !== 'air' && !/water|lava/.test(b.name) && b.diggable && chunk(b.position) === chunk(victim.position)) {
        second = b;
        break;
      }
    }
    if (second) {
      try {
        await bert.dig(second, true);
      } catch {
        // abgebrochen
      }
      await sleep(1000);
    }
    check('Nach /gs vertrauen darf Bert bauen (laut Server weg)', second != null && await serverHas(second.position, 'air'),
      second?.name);
  } else {
    check('Grundstück geschützt: Bert kann nicht abbauen', false, 'kein Block');
  }
  await cmd('gamemode survival Bert');
  const info = await say(anna, '/gs', /gehört Anna|gehört/);
  check('/gs info zeigt den Besitzer', /gehört Anna/.test(info), info);

  // ------------------------------------------------------------------ Ladenkiste auf dem eigenen Grundstück
  {
    const ap = anna.entity.position.floored();
    const chunkOf = (x, z) => `${Math.floor(x / 16)}:${Math.floor(z / 16)}`;
    let spot = null;
    for (const [dx, dz] of [[2, 0], [-2, 0], [0, 2], [0, -2], [2, 2], [-2, -2]]) {
      if (chunkOf(ap.x + dx, ap.z + dz) === chunkOf(ap.x, ap.z)) {
        spot = ap.offset(dx, 0, dz);
        break;
      }
    }
    await cmd(`setblock ${spot.x} ${spot.y} ${spot.z} chest`, `setblock ${spot.x} ${spot.y + 1} ${spot.z} oak_sign`,
      'clear Anna', 'give Anna cobblestone 32');
    await sleep(1000);
    await hold(anna, 'cobblestone');
    const signBlock = anna.blockAt(spot.offset(0, 1, 0));
    await anna.lookAt(signBlock.position.offset(0.5, 0.5, 0.5), true);
    await anna.activateBlock(signBlock);
    await sleep(700);
    const setup = nextWindow(anna);
    anna.updateSign(anna.blockAt(spot.offset(0, 1, 0)), '[Laden]');
    const sw = await setup;
    check('[Laden]-Schild öffnet „Laden einrichten“', /Laden einrichten/.test(title(sw)), title(sw));
    clear(anna);
    if (sw) await click(anna, 16);
    await waitFor(() => sees(anna, /Laden für .* ist offen/), 3000);
    check('Laden ist offen', sees(anna, /Laden für .* ist offen/), last(anna, /Laden/));
    await cmd(`item replace block ${spot.x} ${spot.y} ${spot.z} container.0 with cobblestone 64`, 'tp Bert Anna');
    await sleep(2000);
    const annaBefore = await balance(anna);
    const bertBefore = await balance(bert);
    const cw2 = nextWindow(bert);
    await bert.lookAt(spot.offset(0.5, 0.5, 0.5), true);
    await bert.activateBlock(bert.blockAt(spot));
    const shopWin = await cw2;
    check('Kunde sieht den Laden statt der Kiste', /Laden von Anna/.test(title(shopWin)), title(shopWin));
    clear(anna, bert);
    if (shopWin) await click(bert, 13);
    await waitFor(() => sees(bert, /Gekauft: 32×/), 3000);
    check('Bert kauft 32 Steine aus dem Laden', sees(bert, /Gekauft: 32× .* bei Anna/) && count(bert, 'cobblestone') === 32,
      last(bert, /Gekauft/));
    if (bert.currentWindow) bert.closeWindow(bert.currentWindow);
    const price = bertBefore - (await balance(bert));
    check('Geld ging von Bert an Anna (keine Laden-Gebühr)', price > 0 && (await balance(anna)) === annaBefore + price,
      `${price} ₮`);
    check('Ladenkiste selbst kann Bert nicht abbauen', await (async () => {
      await cmd('gamemode creative Bert');
      try {
        await bert.dig(bert.blockAt(spot), true);
      } catch {
        // abgebrochen
      }
      await sleep(800);
      await cmd('gamemode survival Bert');
      return serverHas(spot, 'chest');
    })());
  }

  // ------------------------------------------------------------------ Zuhause
  const base = anna.entity.position.clone();
  check('/sethome basis', (await say(anna, '/sethome basis', /gesetzt/)) !== '');
  anna.chat('/spawn');
  const toTown = await teleported(anna, base, 15000);
  check('/spawn bringt in die Stadt (Spawn-Welt)', toTown && await inDim('Anna', 'minecraft:smp_spawn'),
    anna.entity.position.toString());
  await sleep(1500);
  const inTown = anna.entity.position.clone();
  anna.chat('/home basis');
  const home = await teleported(anna, inTown, 15000);
  check('/home basis bringt zurück', home && anna.entity.position.distanceTo(base) < 2, anna.entity.position.toString());
  await sleep(1000);
  const homes = await say(anna, '/homes', /Deine Zuhause/);
  check('/homes listet das Zuhause', homes !== '');

  // ------------------------------------------------------------------ Kampf in der Wildnis + Schutz in der Stadt
  await cmd('tp Bert Anna');
  await sleep(2000);
  // Annas Grundstück ist friedlich: kein Schaden
  const hp0 = anna.health;
  const annaEntity = bert.players.Anna?.entity;
  if (annaEntity) {
    await bert.lookAt(annaEntity.position.offset(0, 1.4, 0), true);
    bert.attack(annaEntity);
    await sleep(800);
  }
  check('Kein Schaden auf friedlichem Grundstück', anna.health === hp0, `${hp0} → ${anna.health}`);

  // Grundstück aufgeben → Wildnis: jetzt geht Kämpfen, beide sind „im Kampf“
  check('/gs aufgeben', (await say(anna, '/gs aufgeben', /freigegeben/)) !== '');
  clear(anna, bert);
  const hp1 = anna.health;
  const target2 = bert.players.Anna?.entity;
  if (target2) {
    await bert.lookAt(target2.position.offset(0, 1.4, 0), true);
    bert.attack(target2);
  }
  await waitFor(() => anna.health < hp1, 3000);
  check('In der Wildnis wirkt ein Treffer', anna.health < hp1, `${hp1} → ${anna.health}`);
  check('Kampf-Markierung für beide', await waitFor(() => sees(anna, /im Kampf/) && sees(bert, /im Kampf/), 3000));
  const blocked = await say(anna, '/spawn', /Im Kampf geht das nicht/);
  check('Im Kampf kein /spawn', blocked !== '', blocked);

  // Flucht im Kampf: Bert geht → stirbt, Annas Kopfgeld auf Bert (100 ₮) geht an Anna
  const richBefore = await balance(anna);
  clear(anna);
  if (bert.players.Anna?.entity) bert.attack(bert.players.Anna.entity);
  await sleep(500);
  bert.quit();
  await waitFor(() => sees(anna, /geflüchtet/) && sees(anna, /Kopfgeld kassiert/), 5000);
  check('Flucht im Kampf wird gemeldet', sees(anna, /Bert ist mitten im Kampf geflüchtet/), last(anna, /geflüchtet/));
  check('Kopfgeld (100 ₮) geht an Anna', sees(anna, /Anna hat Bert besiegt und 100 ₮ Kopfgeld kassiert/) &&
    (await balance(anna)) === richBefore + 100, last(anna, /Kopfgeld/));

  // Echtes Netherportal in der Oberwelt (Vanilla): Rahmen per Konsole, anzünden, hineinstellen → Nether
  {
    await sleep(16000); // Kampf vorbei (Teleport im Kampf gesperrt)
    const q = anna.entity.position.floored();
    const x = q.x + 3;
    const y = q.y + 1;
    const z = q.z;
    await cmd(`execute in minecraft:overworld run fill ${x - 1} ${y - 1} ${z - 1} ${x + 4} ${y + 5} ${z + 1} air`,
      `execute in minecraft:overworld run fill ${x} ${y - 1} ${z} ${x + 3} ${y + 3} ${z} obsidian`,
      `execute in minecraft:overworld run fill ${x + 1} ${y} ${z} ${x + 2} ${y + 2} ${z} air`,
      `execute in minecraft:overworld run setblock ${x + 1} ${y} ${z} fire`);
    await sleep(800);
    const lit = await serverHas({ x: x + 1, y, z }, 'nether_portal');
    check('Netherportal in der Oberwelt lässt sich entzünden', lit);
    await cmd(`execute in minecraft:overworld run tp Anna ${x + 1.5} ${y} ${z + 0.5}`);
    const nether = await waitFor(async () => await inDim('Anna', 'minecraft:the_nether'), 20000, 500);
    check('Durchs Netherportal in den Nether (Vanilla)', nether, anna.entity.position.toString());
  }

  // ------------------------------------------------------------------ Händlerhaus (/shop)
  // Bert ist nach der Flucht im Kampf weg – Dora (neu) kauft ein
  const dora = await join('Dora');
  await cmd('smpadmin geld setzen Dora 1000', 'clear Dora');
  await sleep(600);
  const sm = await openBy(dora, '/shop');
  check('/shop öffnet das Händlerhaus', /Händlerhaus/.test(title(sm)), title(sm));
  const catNames = [11, 12, 13, 14, 15, 20, 21, 22, 23, 24].map((i) => plain(sm?.slots[i]?.customName));
  check('Zehn Abteilungen mit Symbol', catNames.every((n) => n.length > 2) && /Baublöcke/.test(catNames[0]),
    catNames.join(', '));
  check('Kontostand im Menü sichtbar', /Dein Geld: 1\.000 ₮/.test(itemText(sm?.slots[49])), itemText(sm?.slots[49]));
  const cw1 = await clickOpen(dora, 11);
  check('Abteilung „Baublöcke“ öffnet sich (Seite 1)', /Baublöcke.*1\/\d/.test(title(cw1)), title(cw1));
  const stoneText = itemText(cw1?.slots[9]);
  check('Ware mit Kauf- und Ankaufspreis im Tooltip', cw1?.slots[9]?.name === 'stone' &&
    /Kaufen: 0,60 ₮.*Verkaufen: 0,10 ₮/.test(stoneText), stoneText);
  const ww = await clickOpen(dora, 9);
  check('Ware-Menü (Stein)', /stone/i.test(title(ww)), title(ww));
  check('Kauf-Knopf 64× zeigt Kosten', /Kaufen: 64×.*Kosten: 38,40 ₮/.test(itemText(ww?.slots[15])), itemText(ww?.slots[15]));
  clear(dora);
  let reopen = nextWindow(dora);
  await click(dora, 15);
  await waitFor(() => sees(dora, /Gekauft/), 4000);
  check('64 Stein gekauft (38,40 ₮)', sees(dora, /Gekauft: 64× .*38,40 ₮\. Noch 961,60 ₮/), last(dora, /Gekauft/));
  await waitFor(() => count(dora, 'stone') === 64, 3000);
  check('Stein liegt im Inventar', count(dora, 'stone') === 64, String(count(dora, 'stone')));
  await reopen;
  if (dora.currentWindow) dora.closeWindow(dora.currentWindow);
  await sleep(400);

  const ew = await openBy(dora, '/shop erze');
  check('/shop erze öffnet „Erze & Edelsteine“', /Erze/.test(title(ew)), title(ew));
  const dw2 = await clickOpen(dora, slotOf(ew, 'diamond'));
  clear(dora);
  await click(dora, 13); // 16 Diamanten = 1.280 ₮
  await waitFor(() => sees(dora, /reicht dein Geld nicht/), 3000);
  check('Zu wenig Geld: klare Meldung mit Fehlbetrag', sees(dora, /dir fehlen 318,40 ₮/),
    `${title(dw2)} | ${itemText(dw2?.slots[13]).slice(0, 120)} | ${dora.chats.join(' / ').slice(0, 300)}`);
  check('Nichts abgebucht', (await balance(dora)) === 961.6);
  void dw2;
  if (dora.currentWindow) dora.closeWindow(dora.currentWindow);

  await cmd('smpadmin geld geben Dora 5000');
  const xw = await openBy(dora, '/shop ender chest');
  check('Suche „ender chest“ findet die Endertruhe', slotOf(xw, 'ender_chest') >= 9, title(xw));
  const ecw = await clickOpen(dora, slotOf(xw, 'ender_chest'));
  check('Tagesgrenze im Tooltip', /Heute noch 2 zu haben/.test(itemText(ecw?.slots[4])), itemText(ecw?.slots[4]));
  clear(dora);
  reopen = nextWindow(dora);
  await click(dora, 11); // 1×
  await waitFor(() => sees(dora, /Gekauft/), 3000);
  await reopen;
  check('Endertruhe gekauft (70 ₮)', sees(dora, /Gekauft: 1× .*70 ₮/), last(dora, /Gekauft/));
  check('Mengen über der Tagesgrenze gibt es gar nicht als Knopf', dora.currentWindow?.slots[12]?.name === 'lime_stained_glass_pane',
    dora.currentWindow?.slots[12]?.name);
  clear(dora);
  await click(dora, 16); // eigene Menge: 2 → nur noch 1
  await waitFor(() => sees(dora, /Wie viele/), 3000);
  dora.chat('2');
  await waitFor(() => sees(dora, /heute nur noch/), 3000);
  check('Tagesgrenze: „heute nur noch 1“', sees(dora, /heute nur noch 1 für dich/), last(dora, /nur noch/));
  if (dora.currentWindow) dora.closeWindow(dora.currentWindow);

  const gw = await openBy(dora, '/shop goldener apfel');
  const gaSlot = slotOf(gw, 'golden_apple');
  check('Suche „goldener apfel“ (deutsch)', gaSlot >= 9, title(gw));
  const gaw = await clickOpen(dora, gaSlot);
  clear(dora);
  await click(dora, 16); // eigene Menge → Frage im Chat
  await waitFor(() => sees(dora, /Wie viele/), 3000);
  check('Eigene Menge: Frage im Chat', sees(dora, /Wie viele\?/), last(dora, /Wie viele/));
  const confWin = nextWindow(dora);
  dora.chat('4'); // 4× = 400 ₮ → Nachfrage
  const conf = await confWin;
  check('Ab 250 ₮ fragt die Händlerin nach', /Wirklich kaufen/.test(title(conf)), title(conf));
  clear(dora);
  await click(dora, 10);
  await waitFor(() => sees(dora, /Gekauft/), 3000);
  check('Nach „Ja“ gekauft: 4 goldene Äpfel für 400 ₮', sees(dora, /Gekauft: 4× .*400 ₮/) && count(dora, 'golden_apple') === 4,
    last(dora, /Gekauft/));
  void gaw;
  if (dora.currentWindow) dora.closeWindow(dora.currentWindow);
  await sleep(400);

  await cmd('give Dora wheat 100');
  await sleep(600);
  const fw2 = await openBy(dora, '/shop weizen');
  const whw = await clickOpen(dora, slotOf(fw2, 'wheat'));
  check('Verkaufen-Knopf zeigt den Erlös (16 Weizen = 8 ₮)', /Verkaufen: 16×.*Du bekommst: 8 ₮/.test(itemText(whw?.slots[31])),
    itemText(whw?.slots[31]));
  clear(dora);
  reopen = nextWindow(dora);
  await click(dora, 31);
  await waitFor(() => sees(dora, /Verkauft: 16×/), 3000);
  check('16 Weizen verkauft (+8 ₮)', sees(dora, /Verkauft: 16× .*\+8 ₮/), last(dora, /Verkauft/));
  await reopen;
  await sleep(300);
  check('„Alle verkaufen“ zeigt 84', /Alle verkaufen \(84\)/.test(itemText(dora.currentWindow?.slots[34])),
    itemText(dora.currentWindow?.slots[34]));
  clear(dora);
  reopen = nextWindow(dora);
  await click(dora, 34);
  await waitFor(() => sees(dora, /Verkauft: 84×/), 3000);
  check('Alle verkaufen: 84 Weizen (+42 ₮)', sees(dora, /Verkauft: 84× .*\+42 ₮/) && count(dora, 'wheat') === 0,
    last(dora, /Verkauft/));
  await reopen;
  clear(dora);
  await click(dora, 33); // 64 verkaufen, aber keiner mehr da
  await waitFor(() => sees(dora, /Du hast nur/), 3000);
  check('Verkaufen ohne Ware: „Du hast nur 0 davon“', sees(dora, /Du hast nur 0 davon/), last(dora, /hast nur/));
  if (dora.currentWindow) dora.closeWindow(dora.currentWindow);

  await cmd('give Dora bone 10', 'give Dora rotten_flesh 20', 'give Dora torch 5');
  await sleep(600);
  const sa = await openBy(dora, '/shop verkaufen');
  check('Inventar verkaufen: Übersicht vorher', /Wirklich verkaufen/.test(title(sa)) && /Stein|Stone|stone/i.test(itemText(sa?.slots[13])),
    itemText(sa?.slots[13]).slice(0, 200));
  clear(dora);
  await click(dora, 10);
  await waitFor(() => sees(dora, /Sorten\) verkauft/), 3000);
  check('Inventar verkauft: 94 Sachen, 3 Sorten, +11,40 ₮', sees(dora, /94 Sachen \(3 Sorten\) verkauft: \+11,40 ₮/),
    last(dora, /verkauft/));
  check('Fackeln (kauft sie nicht an) und Äpfel bleiben', count(dora, 'torch') === 5 && count(dora, 'golden_apple') === 4
    && count(dora, 'stone') === 0 && count(dora, 'bone') === 0);
  check('Kontostand stimmt genau: 5.553 ₮', (await balance(dora)) === 5553, String(await balance(dora)));
  const shopAdm = (await cmd('smpadmin shop')).join(' ');
  // Käufe: Stein 38,40 + Endertruhe 70 + Äpfel 400; Ankauf: Anna 32 + 123 (vorher), Dora 8 + 42 + 11,40
  check('Kassenbuch: /smpadmin shop zeigt Umsatz und Ankauf genau', /kauften für (?:§.)*508,40 ₮(?:§.)* \(3 Käufe\)/.test(shopAdm)
    && /zahlte (?:§.)*216,40 ₮(?:§.)* \(5 Verkäufe\)/.test(shopAdm), shopAdm.slice(0, 260));

  dora.quit();

  // ------------------------------------------------------------------ Statistik/Admin
  const adm = await cmd('smpadmin info');
  check('/smpadmin info (Konsole)', /Geld bei Spielern/.test(adm.join(' ')), adm.join(' ').slice(0, 160));
  void afterStall;

  // ------------------------------------------------------------------ Netzwerk (Test-Ersatz schreibt calls.log)
  if (NETWORK) {
    const cleo = await join('Cleo');
    const en = await say(cleo, '/geld', /balance/i);
    check('Sprache vom Netzwerk: Englisch für Cleo', /Your balance: 100 ₮/.test(en), en);
    const enShop = await openBy(cleo, '/shop');
    check('Händlerhaus auf Englisch', /Merchant house/.test(title(enShop)) &&
      /Your money: 100 ₮/.test(itemText(enShop?.slots[49])) && /Building blocks/.test(plain(enShop?.slots[11]?.customName)),
      `${title(enShop)} / ${itemText(enShop?.slots[49])}`);
    if (cleo.currentWindow) cleo.closeWindow(cleo.currentWindow);
    cleo.quit();
    const final = await balance(anna);
    const log = path.join(SERVER, 'plugins/MoinMorni/calls.log');
    const calls = () => (fs.existsSync(log) ? fs.readFileSync(log, 'utf8') : '').split('\n').filter(Boolean)
      .map((l) => JSON.parse(l));
    const sum = (name, key) => calls().filter((c) => c.t === 'stats' && c.mode === 'mirni' && c.name === name)
      .reduce((n, c) => n + (c.add[key] ?? 0), 0);
    await waitFor(() => sum('Anna', 'balance') === final * 100, 40000, 1000);
    check('Netzwerk-Statistik: Kontostand von Anna stimmt (in Hundertsteln)', sum('Anna', 'balance') === final * 100,
      `${sum('Anna', 'balance')} / ${final * 100}`);
    check('Netzwerk-Statistik: Verkäufe Anna ≥ 2 (Auktion + Laden)', sum('Anna', 'sales') >= 2, String(sum('Anna', 'sales')));
    check('Netzwerk-Statistik: Einkäufe Bert ≥ 2, erfüllte Aufträge Anna = 1',
      sum('Bert', 'purchases') >= 2 && sum('Anna', 'orders_filled') === 1,
      `${sum('Bert', 'purchases')} / ${sum('Anna', 'orders_filled')}`);
    check('Netzwerk-Zustand „running“ für mirni', calls().some((c) => c.t === 'state' && c.mode === 'mirni' &&
      c.state === 'running'));
  }

  anna.quit();
  await sleep(1000);
  const ok = results.filter((r) => r.ok).length;
  console.log(`\n${ok}/${results.length} Prüfungen bestanden`);
  process.exit(ok === results.length ? 0 : 1);
}

async function bot_shift(bot, slot) {
  await bot.clickWindow(slot, 0, 1);
  await sleep(150);
}

main().catch((e) => {
  console.error(e);
  const ok = results.filter((r) => r.ok).length;
  console.log(`\nABBRUCH nach ${ok}/${results.length}`);
  process.exit(1);
});
