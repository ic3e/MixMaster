/*
 * Pour Day — one slab, one day, in first person.
 *
 * The day runs on a game clock. What you do moves the clock at a pace that suits it (a walk to
 * the van is a minute or two, waiting for concrete can be hours), and the slab hardens by the
 * weather: warm, dry, windy and thick go faster; cold, damp, thin and a soupy mix go slower.
 */
(function () {
  'use strict';

  // ------------------------------------------------------------------ helpers
  const $ = (s) => document.querySelector(s);
  const rnd = (a, b) => a + Math.random() * (b - a);
  const irnd = (a, b) => Math.floor(rnd(a, b + 1));
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
  const chance = (p) => Math.random() < p;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const lerp = (a, b, t) => a + (b - a) * t;
  const hyp = (ax, az, bx, bz) => Math.hypot(ax - bx, az - bz);
  const DEBUG = /[?&]debug\b/.test(location.search);
  // inside MixMaster the app hands the page a way out, and a voice; in a plain browser, neither
  const appBridge = window.MixMaster && typeof window.MixMaster.quit === 'function' ? window.MixMaster : null;
  const CALM = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);

  function clock(m) {
    const mm = Math.floor(m);
    const h = Math.floor((((mm % 1440) + 1440) % 1440) / 60);
    const mi = ((mm % 60) + 60) % 60;
    return String(h).padStart(2, '0') + ':' + String(mi).padStart(2, '0');
  }
  function clockDay(m) { return clock(m) + (m >= 1440 ? ' (next day)' : ''); }
  function dur(min) {
    min = Math.max(0, Math.round(min));
    const h = Math.floor(min / 60), m = min % 60;
    return h ? (m ? `${h} h ${m} min` : `${h} h`) : `${m} min`;
  }
  function weighted(list) {
    const total = list.reduce((s, x) => s + x[0], 0);
    let r = Math.random() * total;
    for (const x of list) { r -= x[0]; if (r <= 0) return x[1]; }
    return list[list.length - 1][1];
  }
  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      localStorage.setItem(key, value);
    } catch (e) { return null; }
    return null;
  }

  // ------------------------------------------------------------------ the site
  const NX = 22, NZ = 16;           // the ground a slab can take: 22 x 16 m, in 1 m cells
  const SLAB = { x0: -16, x1: 6, z0: -8, z1: 8 };
  const EYE = 1.7;
  const REACH = 5.5;
  const P = (x, z) => ({ x, z });
  const POS = {
    van: P(-26, 9), vanDoor: P(-23.4, 8.0),
    ibc: P(-21, -9), ibcFront: P(-21, -7.6),
    kiosk: P(-38, -24), kioskFront: P(-35.4, -22.2),
    tripod: P(-8.6, 0.6),
    tarp: P(-19.5, 5.4),
    pump: P(21.5, -3), pumpOut: P(17.9, -2.6),
    mixer: P(23, 4.5),
    pile: P(16.2, 1.6),
    loo: P(-31, -15),
    office: P(-34, 13),
  };
  const PIPE_ROUTE = [P(16.3, -2.3), P(14.1, -2.0), P(11.9, -1.6), P(9.7, -1.1), P(7.7, -0.6), P(6.4, -0.2)];
  const TRUCK_M3 = 8;                 // a full mixer; a small pour comes as a part load

  // ------------------------------------------------------------------ what people say
  const L = {
    alarm: [
      '04:45. The alarm. Your back already knows what day it is.',
      '04:45. The alarm goes off. So does your knee, in sympathy.',
      '04:45. It is dark, it is cold, and somewhere a concrete plant is warming up just for you.',
    ],
    drive: [
      ['Clear roads. Suspicious.', 0],
      ['Stuck behind a tractor doing 23 km/h. The farmer waves. You do not.', 12],
      ['The petrol station coffee machine is "being cleaned". For the third week.', 6],
      ['Roadworks. Three men watching one man dig. You feel at home.', 9],
    ],
    arrive: [
      'First on site. The birds look at you like you owe them money.',
      'Nobody here. Just you, the slab base and a cat that belongs to no one.',
      'The site is quiet. Enjoy it. It will not last.',
    ],
    pumpArrive: [
      '"Morning. Where do you want the pipes? Don\'t say \'in the van\'."',
      '"I\'ve been driving since four. If this pour blocks, I\'m blaming you personally."',
      '"Nice base. Shame about what\'s going to happen to it."',
    ],
    pumpLate: [
      'Pump driver: "Running a bit late, the last site had a dog." ETA {eta}.',
      'Pump driver: "Coming! Just finishing my second breakfast." ETA {eta}.',
      'Pump driver: "The satnav took me to a lake. I\'m on my way." ETA {eta}.',
    ],
    truckLate: [
      'Plant: "The truck left ten minutes ago." It did not. ETA {eta}.',
      'Plant: "The driver is on his way, he just had to finish his sausage." ETA {eta}.',
      'Plant: "Traffic." Plant always says traffic. ETA {eta}.',
    ],
    truckDriver: [
      '"Plant says S3. The plant also says it loves you."',
      '"Where do you want it? Don\'t say \'on the slab\', everyone says that."',
      '"Quick one today? I have a funeral at two. Mine, if I\'m late."',
    ],
    pipe: [
      'Clunk. The coupling bites your finger. You learn a new word.',
      'Pipe in. It weighs exactly as much as you remember, plus two kilos.',
      'You clamp it. The clamp clamps you back.',
      'Pipe down. Your back files a complaint with HR.',
    ],
    pourJokes: [
      'Pump driver, from his remote: "Faster! I get paid by the hour, but I don\'t like it."',
      'The mixer driver starts his crossword. Seven letters, "grey and heavy". He writes "MONDAYS".',
      'Somebody on the pavement films you. Wave. Now you\'re content.',
      'Your phone buzzes. It\'s the foreman asking if it\'s done yet. It is 08:15.',
      'A bird lands on the formwork, looks at the concrete, and decides against it. Smart bird.',
    ],
    falls: [
      'You sit down in it. The slab now has a perfect print of your behind.',
      'Your boot stays. You don\'t. Graceful, in a way.',
      'You slip, flail, and land on your butt. The pump driver claps.',
      'Down you go. Somewhere, a health and safety officer feels a chill.',
    ],
    stuck: [
      'Your boot is stuck. You stand there like a garden gnome until it lets go.',
      'The concrete has your left boot. It is negotiating.',
    ],
    blocked: [
      'BANG. The line is blocked. The pump driver looks at you. You look at the pipe. Hit it.',
      'The pump groans and stops. Blocked. Somewhere a pipe needs a hammer.',
    ],
    unblocked: [
      'Clang clang clang. It coughs, spits and runs again. So does your nose.',
      'You hit the pipe. It forgives you. The pump driver does not.',
    ],
    blowout: 'The formwork on the {side} opens up. Concrete is leaving the building. Fix it!',
    battery: 'The laser beeps once and dies. The spare batteries are in the van. Of course they are.',
    wash: [
      'The pump driver hoses down his pipes, the pump, your van and, briefly, you.',
      'The pump driver washes out his pipes. The run-off heads straight for the neighbour\'s rose bed.',
    ],
    thumb: [
      [15, 'Your thumb goes in to the second knuckle. It is basically porridge.'],
      [25, 'Thumb leaves a deep print. It gives like a good mattress.'],
      [45, 'A clear print. You could start the pans soon.'],
      [65, 'A faint print. Blades are coming.'],
      [90, 'Barely a mark. It is getting there.'],
      [101, 'Nothing. It is hard. Like the foreman\'s heart.'],
    ],
    coffee: [
      'Thermos coffee. Tastes like 6 a.m. and diesel. Perfect.',
      'You pour a cup. The steam is the only warm thing on this site.',
      'Coffee. Your heart rate rejoins the conversation.',
    ],
    noCoffee: 'The thermos is empty. So is your soul. The kebab stand sells coffee.',
    cross: [
      { who: 'Neighbour in slippers', ask: '"Can I just quickly cross? My car\'s on the other side. I\'ll step lightly."', back: '"Charming. I\'ll tell your mother."' },
      { who: 'The electrician', ask: '"Mate, I just need to get to that socket. Two steps. Tiny steps."', back: '"Fine! I\'ll go round. Like an animal."' },
      { who: 'The client', ask: '"It\'s technically my concrete. I\'ll just have a look from the middle."', back: '"I\'m paying for this, you know." You know.' },
      { who: 'Delivery driver', ask: '"Parcel for... someone? Is this the shortcut?"', back: '"Wow. Okay. Five stars anyway."' },
      { who: 'Site manager in white trainers', g: 'm', ask: '"Just checking progress! Don\'t mind me."', back: '"I\'ll write that down." He does not have a pen.' },
      { who: 'A jogger', ask: '(already jogging towards the slab) "Sorry! I\'m on a streak!"', back: '"Rude!" (jogs off, streak intact)' },
      { who: 'Man with a clipboard', g: 'm', ask: '"Council inspection. I need to measure something in the middle."', back: '"I\'ll measure it from here then." He squints heroically.' },
      { who: 'The plumber', ask: '"My van\'s just there. Straight line is fastest, yeah?"', back: '"Wow. And I thought plumbers were rude."' },
      { who: 'A man walking a pram', g: 'm', ask: '"It\'s set by now, surely? It looks set."', back: '"It LOOKS set." It is not set.' },
      { who: 'Two teenagers', ask: '"Bro, can we walk on it? For content?"', back: '"Unsubscribed." They film you instead.' },
      { who: 'The architect', g: 'm', ask: '"I just want to feel the space. From the middle. Barefoot, ideally."', back: '"The space will have to wait, then." He feels it from the edge.' },
      { who: 'Lady with shopping bags', g: 'f', ask: '"Oh, it\'s wet? It doesn\'t look wet. I\'ll be quick."', back: '"In my day builders were polite." In her day builders were you.' },
      { who: 'Postman', g: 'm', ask: '"Letter for the building. Which is where, exactly? Over there?"', back: '"The building doesn\'t even exist yet!" He wanders off to deliver it to a hole.' },
      { who: 'The neighbour\'s kid', g: 'kid', ask: '"Can I write my name in it? Just my initials. Small."', back: '"You\'re no fun." Correct.' },
      { who: 'Surveyor with a tripod', ask: '"I need a point in the middle. Two minutes. Three legs, very light."', back: '"Three legs, three holes. Understood."' },
    ],
    hellLabels: [
      'Go to hell!', 'Around. AROUND.', 'Walk on it and you live in it.', 'Over my dead trowel.',
      'Not a chance, pal.', 'Do I look like a zebra crossing?', 'Try it and see what happens.', 'It\'s wet. Like my patience: gone.',
      'Round the outside, or round the hospital.', 'Absolutely not. With love.',
    ],
    shoutBack: [
      '"Alright, alright!" They turn round and go the long way.',
      '"Nobody told me!" There are four signs. They go round.',
      '"Sorry, boss." They back off the way they came, tiptoeing, which helps nobody.',
      '"Jeez. Fine." They walk round, making a point of how far it is.',
    ],
    shoutHurry: [
      'They hear "run". They run. Across it. Faster. Deeper.',
      '"I\'m nearly over!" They are not nearly over.',
      'They speed up, apologising with every single footprint.',
    ],
    shoutFreeze: [
      'They freeze mid-step like a gnome. Then they carry on, as if the shout was weather.',
      'They stop, look at you, look at their shoe, and keep going. Slower. Deeper.',
    ],
    driverTalk: [
      'Pump driver: "You know what the difference is between a pump driver and a concrete finisher? I go home at eleven."',
      'Pump driver: "Stiffer mix next time and I\'m charging for the swearing."',
      'Pump driver: "My wife thinks I\'m a pilot. Don\'t tell her."',
      'Pump driver: "I once pumped a whole pool through a keyhole. Different day. Different keyhole."',
      'Pump driver: "Is that your float? Looks like it\'s seen things."',
    ],
    mixTalk: [
      'Mixer driver: "Plant says it\'s S3. Plant also says it\'s Tuesday."',
      'Mixer driver: "Take your time. I\'m paid by the hour. You\'re paying, by the hour."',
      'Mixer driver: "If you want it wetter, I\'ve got a hose. If you want it drier, I\'ve got a hose and regrets."',
      'Mixer driver: "Thirty years on the drum. Still dizzy."',
    ],
    crossAnyway: [
      'They walk across anyway. Confident stride, size 45. Perfect prints.',
      'They nod, agree with everything you said, and step straight on it.',
      '"I\'m light!" They are not light.',
      '"I\'ll walk on the lines." There are no lines. There are only footprints now.',
      'They take their shoes off first, as a courtesy. Barefoot prints. Toes and everything.',
    ],
    crossAway: [
      'While you were away someone crossed the slab. Size 45, confident stride. Probably the electrician.',
      'You find footprints across the slab. They stop in the middle, turn around, and go back. Why.',
      'Someone crossed while you were gone. There\'s a coffee cup lid in the middle as a signature.',
      'While you were away someone walked across, stopped in the middle and, from the prints, did a little dance.',
    ],
    dog: {
      who: 'A dog',
      ask: 'A dog trots up to the edge of the slab. It looks at you. It looks at the slab. It has made its decision.',
      away: [
        'Paw prints. Lots of them. In circles. The dog had a lovely time.',
        'While you were gone a dog did three laps of the slab. You can tell it was happy.',
      ],
    },
    dogShoo: [
      'The dog gives you a look of deep disappointment and trots off.',
      'SHOO works. The dog leaves, slowly, to show it was its own idea.',
    ],
    dogGame: [
      'The dog hears "PLAY WITH ME". Laps of honour on the slab.',
      'The dog thinks shouting is a game. It is winning.',
    ],
    dogSausage: 'The dog catches the sausage mid-air and leaves with it. Best trade of the day.',
    bird: 'A seagull landed on the slab, walked three steps, and left you a little something extra.',
    foreman: [
      '"Is it hard yet? The client wants to drive a forklift on it at two."',
      '"Quick one — can we do the second floor tomorrow? There is no second floor. Doesn\'t matter."',
      '"Just checking you\'re not on your phone." You are, because he called.',
    ],
    lunch: 'KEBAB & COFFEE. The owner nods at you like he knows exactly how your day is going.',
    vanNap: [
      'You wake up with the seatbelt printed on your face.',
      'You dreamt about troweling. Very relaxing. Then you woke up and had to do it.',
      'You nap. The radio plays the same four songs. Your dream now has a chorus.',
    ],
    formOk: [
      'Formwork is solid. The carpenter lives to see another day.',
      'Every stake knocked twice for luck. Solid. Suspiciously solid.',
    ],
    formWeak: [
      'Two stakes here were only resting in the ground. Knocked in. That would have been a river.',
      'This board was held on with hope and one nail. Three more nails. Now it\'s held on with nails.',
    ],
    tie: [
      'Wire round, three twists, snip. The bar stays down. Your fingers do not thank you.',
      'Tied. The mesh no longer wants to float up through your nice slab.',
      'A tie, a twist, a word you don\'t say in front of the client.',
    ],
    cut: [
      'Snip. The bar that was waiting to impale somebody is now a stub.',
      'You cut the bar. It pings off into the fence. Nobody saw. Probably.',
    ],
    tooSoftMachine: 'It\'s soup. The machine would sink to the gearbox. Give it time.',
    tooSoftBlades: 'Blades on this? It would tear the paste off. Pans first, and patience.',
    sleepy: 'You fell asleep leaning on the rake. {m} minutes gone. The rake is fine.',
    managerCall: [
      '"{n} MARKS? Did you pour a slab or host a line-dancing competition? The client wanted polished concrete, not a map of your day!"',
      '"You know what {n} footprints is? It\'s a trail. And it leads straight to the job centre."',
      '"The client asked me if the footprints are a design feature. I said yes. So now you\'re going to design me a new slab. For free."',
      '"{n}. I counted them from the photo you didn\'t send me. The client sent it. With a lot of question marks."',
      '"Every one of those {n} marks is coming out of your Christmas party. You are not going to the Christmas party."',
    ],
    managerAfter: [
      'He hangs up. Your ear is still ringing at 95%.',
      'He hangs up mid-word. You suspect the word was not "well done".',
      'The call ends. A pigeon on the formwork looks at you with something like pity.',
    ],
    emptyTruck: [
      'The driver swings the chute round, pulls the lever, and... nothing. "It was full when I left." It was not full when he left.',
      'The drum turns. Nothing comes out. The driver climbs up, looks in, climbs down. "Funny story."',
      'Empty. The load went to a site with a similar name. That site is having a very good day.',
    ],
    wrong: {
      grade: { title: 'Wrong concrete.', text: 'The delivery note says C20/25. You ordered C30/37. The driver: "It\'s all grey, mate."', used: 'You poured C20 into a C30 slab. The engineer will find out. Engineers always find out.', row: 'C20 instead of C30', cost: 80 },
      screed: { title: 'That\'s not concrete.', text: 'It\'s floor screed. Sand, cement and optimism. It pours like soup and it will cure like it has all week.', used: 'You poured screed and called it a slab. It set eventually. Your reputation did not.', row: 'screed, not concrete', cost: 60 },
      fibre: { title: 'Fibre concrete.', text: 'Nobody ordered fibre concrete. It comes out hairy. The slab will look like a wet dog.', used: 'Fibre concrete nobody ordered. The finished floor has a beard.', row: 'fibre concrete nobody ordered', cost: 40 },
      other: { title: '"Is this Riverside Road?"', text: 'It is not. This load belongs to a site across town, and the right mix for yours is on its way there instead.', used: 'You poured another site\'s concrete. Somewhere across town a very angry man is waiting for yours.', row: 'another site\'s load', cost: 50 },
    },
    ufo: {
      msgs: ['EAT POOP', 'F*** YOU', 'NICE SLAB', 'SEND COFFEE', 'LOL', 'HI', 'NO', '42'],
      seen: 'Lights over the site. A saucer, no joke. It hovers over your slab, hums like a fridge, and burns "{m}" into it in neat capitals. Then it leaves. It didn\'t even say hello.',
      away: 'While you were away something wrote "{m}" into the slab, in perfect capitals, with no footprints leading to it. The dog is not talking.',
      voice: ['Greetings, concrete person. Your slab has been improved.', 'We have travelled forty light years to leave this message. You are welcome.', 'Take us to your foreman. Actually, don\'t.'],
    },
    ball: {
      seen: 'A football bounces straight across your slab, then over the fence. A kid appears at the edge. "Can I have my ball back?" The ball is in the neighbour\'s garden. The dents are in your slab.',
      away: 'Round dents in a line across the slab, like a very heavy rabbit crossed it. Somewhere, a kid has a football and no remorse.',
      kid: '"Can I have my ball back?"',
    },
    cat: {
      seen: 'A cat walks onto the slab like it owns it, turns round three times, and lies down in the middle.',
      stays: 'The cat looks at you, blinks slowly, and stays exactly where it is. It knows you won\'t step on your own slab. Probably.',
      goes: 'The cat gets up, stretches, and leaves at its own speed. Its outline stays behind.',
      away: 'There is a perfect cat-shaped hollow in the middle of the slab, and paw prints leading to it and away from it. Mostly to it.',
    },
    drone: {
      seen: 'A drone buzzes over the site, films your slab from above, and drops out of the sky into it. A teenager waves from the fence. "Can I have it back?" No.',
      away: 'There\'s a drone-shaped crater in the slab. The drone is gone. Someone came for it, on foot, through the concrete.',
      voice: 'Can I have my drone back? It\'s for my channel.',
    },
    bag: {
      seen: 'The wind takes a plastic bag straight across the fresh slab. It bounces, drags, and smears a line through the whole thing, like it had somewhere to be.',
      away: 'A long smear across the slab, and a plastic bag stuck in the far formwork, looking pleased with itself.',
    },
    badPour: {
      thin: ['"You poured it {d} mm thin. The client paid for {t}. Somebody\'s paying the difference, and I\'ll give you a clue: it\'s not the client."', '"Minimalist slab, is it? Minimal slab, minimal pay. I\'m adjusting yours now."'],
      thick: ['"{d} mm over? That\'s not a slab, that\'s a monument. The extra concrete comes out of your wages. Every cubic centimetre."', '"You know concrete costs money? Of course you don\'t. You\'ll know on Friday."'],
      bumpy: ['"The laser says the floor has waves. Surfers love it. The client isn\'t a surfer."', '"±{r} mm. I\'ve seen flatter car parks after an earthquake."'],
      slow: ['"The pour took {h}. The pump charges by the hour. Guess who\'s paying for the extra hour. Hint: it\'s the one reading this."', '"I could have poured that with a teaspoon faster. The teaspoon would also be cheaper."'],
      close: 'He hangs up before you can explain. There was going to be an explanation.',
    },
    pay: {
      base: 'Day rate',
      thin: ['Slab {d} mm thin — the client paid for {t}', 'Minimalist thickness surcharge'],
      thick: ['{d} mm over — free concrete, for the client', 'Monument surcharge ({d} mm proud)'],
      bumpy: ['Waves in the floor (±{r} mm)', 'Surf park fee (±{r} mm)'],
      slow: ['Pump overtime ({m} min of it)', 'The pour took {h}; the pump bills by the hour'],
      marks: ['{n} marks set in for ever, €4 each like a museum', 'Footprint archive ({n} exhibits)'],
      waste: ['{w} m³ dumped behind the office, €90 a cube'],
      wait: ['Waiting time paid to a man doing a crossword in a mixer'],
      late: ['Late by {m} min. Time is money. Yours.'],
      wrong: ['The wrong concrete, poured anyway'],
      manager: ['Emotional damages (the manager\'s)'],
      falls: ['Dry cleaning: {n} × €10'],
      edges: ['Edges closed too late, {n} of them'],
      ufo: ['Unexplained lettering in the slab'],
      hell: ['Told {n} people to go to hell: no charge, company policy'],
      shine: ['Bonus: it actually shines'],
      noPan: ['No pan pass. The client paid for a floor, not a beach'],
      noBlade: ['No blade pass. You\'ll call it "matte finish". They won\'t', 'Shine not included'],
      roughEdges: ['{n} edges left rough, like your manners'],
      thrown: ['{n} tools thrown in the van: dents at cost, plus feelings', 'Tool abuse ({n} airborne)'],
      verdictGood: ['"Not bad. Don\'t let it go to your head, your head is already big enough."', '"Good slab. I\'ll pretend I did it when I tell the client."'],
      verdictBad: ['"That\'s your whole day\'s pay gone. You know what that is? Character building."', '"You owe us money. We\'ll take it in coffee. Six months of coffee."', '"Next time I\'m hiring the dog. It leaves fewer marks and it works for sausages."'],
      verdictMeh: ['"It\'ll do. Things that \'will do\' are why I drink."', '"Could be worse. Could also be a lot better. It\'s mostly the second one."'],
    },
    homeEarly: 'The foreman: "95% or you sleep here." There\'s a sleeping bag in the van for a reason.',
    tooLate: {
      text: 'The slab is as hard as it will ever be, and it isn\'t finished: {what}. Blades now would only polish a stone.\n\nYour phone buzzes. The foreman: "It\'s set, isn\'t it. Throw the kit in the van and go home. We\'ll talk about it tomorrow. We\'ll talk about it a lot."',
      stare: ['You stare at it. It stares back. It doesn\'t get any softer.', 'You poke it with your boot. Your boot loses.', 'You wait for a miracle. The miracle is also on its lunch break.'],
    },
    packUp: {
      start: ['Every finisher\'s dream. You start throwing.', 'You march to the van, and the tools learn to fly.', 'Therapy is expensive. Throwing tools is free.'],
      hand: ['The hammer goes first. It had it coming.', 'The float spins like a helicopter. Nobody claps.', 'The pliers hit the van, then the van floor. Two dents for the price of one.', 'The hand trowel sails in like it knows the way.'],
      machine: ['You throw a power trowel. You didn\'t know you could. Neither did your back.', 'The power trowel lands in the van with a noise the neighbours will describe to the police.'],
      rideOn: 'The ride-on stays where it is. It weighs 400 kg and you are angry, not strong.',
      laser: 'The laser goes in last-but-one. It beeps once in protest.',
      end: 'Door slammed. Engine on. You don\'t look back. The slab does.',
    },
  };

  // ------------------------------------------------------------------ one day
  let day;
  function newDay() {
    const shape = makeShape();
    const season = pick(['winter', 'spring', 'summer', 'summer', 'autumn', 'autumn']);
    const baseTemp = { winter: rnd(-3, 4), spring: rnd(6, 14), summer: rnd(16, 27), autumn: rnd(3, 11) }[season];
    return {
      season,
      baseTemp: Math.round(baseTemp * 10) / 10,
      rh: Math.round(rnd(35, 95)),
      wind: Math.round(rnd(0, 11)),
      thick: pick([80, 100, 120, 150, 150, 180, 200, 250]),
      pumpDelay: pick([0, 0, 0, 15, 25, 45, 90]),
      truckDelays: [pick([0, 0, 10, 20, 40, 60]), pick([5, 15, 25, 45, 70]), pick([10, 20, 35, 60, 90]), pick([15, 40, 80]), pick([20, 50, 90])],
      mix: pick(['stiff', 'ok', 'ok', 'soup']),
      formworkWeak: chance(0.55),
      batteryDies: chance(0.35),
      dogChance: rnd(0.18, 0.32),
      ...shape,
      boom: chance(shape.area > 80 ? 0.65 : 0.35),
      trouble: weighted([[0.55, null], [0.3, 'wrong'], [0.15, 'empty']]),
      troubleTruck: chance(0.6) ? 1 : 2,
      wrongKind: pick(['grade', 'screed', 'fibre', 'other']),
    };
  }
  function tempAt(t) { return day.baseTemp + 4 * Math.sin(2 * Math.PI * (t - 540) / 1440); }
  function volumeNeeded() { return (day.area * day.thick) / 1000; }
  /** What was ordered: the volume and a few per cent, to the half cubic metre, in loads of up to 8 m³. */
  function orderedM3() { return Math.ceil(volumeNeeded() * 1.04 * 2) / 2; }
  function trucksOrdered() { return Math.ceil(orderedM3() / TRUCK_M3); }
  function loadOf(no) {
    if (no <= trucksOrdered()) return Math.min(TRUCK_M3, orderedM3() - TRUCK_M3 * (no - 1));
    return gs.extraLoad || TRUCK_M3;
  }

  /*
   * How fast the slab gains hardness, in % per game minute. Calibrated so a 150 mm slab at 20 °C,
   * 55 % humidity and no wind is ready for pans about three hours after the pour and 95 % hard
   * after about nine. Thicker slabs keep their heat and go faster; a soupy mix has water to lose
   * first. Capped so even a frozen day ends — late.
   */
  function cureRate(t) {
    const fT = clamp(Math.pow(1.7, (tempAt(t) - 20) / 10), 0.2, 2.2);
    const fRH = (1.25 - 0.006 * day.rh) / 0.92;
    const fW = 1 + day.wind / 40;
    const fTh = 0.6 + day.thick / 375;
    return Math.max(0.075, 0.181 * fT * fRH * fW * fTh * gs.mixFactor);
  }
  function stageMul(H) { return H < 15 ? 0.6 : H < 85 ? 1.2 : 0.7; }
  function etaTo(target) {
    if (gs.H >= target) return 0;
    let H = gs.H, t = gs.t, m = 0;
    while (H < target && m < 4000) { H += cureRate(t) * stageMul(H) * 5; t += 5; m += 5; }
    return m;
  }

  // ------------------------------------------------------------------ state
  let gs;
  function freshState() {
    // the whole grid, for looking a square up by where it is; `cells` is just the slab's
    const grid = [];
    for (let j = 0; j < NZ; j++) for (let i = 0; i < NX; i++) {
      grid.push({ i, j, idx: j * NX + i, on: !!(day && day.on[j * NX + i]), fill: 0, pan: 0, blade: 0, covP: false, covB: false, marks: [], defect: false });
    }
    const cells = grid.filter((c) => c.on);
    return {
      t: 4 * 60 + 45, phase: 'title',
      arrived: 0,
      energy: 76, cups: 3, sausage: false,
      tool: 'hands', carrying: null, toolsUnloaded: false,
      tools: {}, fit: { trowelSmall: 'pans', trowelBig: 'pans', rideOn: 'pans' }, fitting: null,
      prep: { unload: false, form: [false, false], laser: false },
      laserOn: false, laserBattery: true, laserPacked: false,
      pumpAt: 0, pumpHere: false, pipes: 0, pipesGone: 0,
      trucks: [], truck: null, truckNo: 0, truckWaitPaid: 0, pourMins: 0, gaveUp: false, packing: null, thrown: null,
      mixFactor: 1, water: 0, mixState: 'ok',
      blocked: -1, blowout: null, blowoutDone: false, batteryDone: false, fellInPour: false, stuckUntil: 0,
      pourStarted: false, pourDone: false, pourEnd: 0, pouredM3: 0, waste: 0, extraTrucks: 0,
      washed: false,
      H: 0, poured: false,
      panPasses: [], bladePasses: [],
      edgesDone: 0, edgeNotes: [],
      waitMode: null, fastForward: null,
      schedule: [], nextNuisance: Infinity, rained: false, lastSleep: 0,
      milestones: {},
      stats: { hell: 0, crossed: 0, dogs: 0, falls: 0, prints: 0, repaired: 0, blockages: 0, coffee: 0, own: 0, dug: 0 },
      story: [],
      grid, cells,
    };
  }
  function at(minute, fn) {
    gs.schedule.push({ at: minute, fn });
    gs.schedule.sort((a, b) => a.at - b.at);
  }
  function remember(text) {
    if (gs.story.includes(text)) return;
    if (gs.story.length < 40) gs.story.push(text);
  }

  // ------------------------------------------------------------------ renderer and scene
  const canvas = $('#gl');
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
  renderer.outputEncoding = THREE.sRGBEncoding;
  const scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0x9ec3e0, 40, 160);
  const camera = new THREE.PerspectiveCamera(72, 1, 0.05, 400);
  camera.rotation.order = 'YXZ';
  scene.add(camera);
  const hemi = new THREE.HemisphereLight(0xdde6f5, 0x584a3b, 0.85);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xfff0d8, 0.9);
  scene.add(sun);
  const flood = new THREE.PointLight(0xffe2b8, 0, 60, 1.2);
  flood.position.set(-21, 8, 3);
  scene.add(flood);

  function resize() {
    const w = window.innerWidth, h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.fov = w < h ? 78 : 68;
    camera.updateProjectionMatrix();
  }
  window.addEventListener('resize', resize);
  resize();

  const MATS = {};
  function lam(c) { return MATS[c] || (MATS[c] = new THREE.MeshLambertMaterial({ color: c })); }
  function mesh(geo, color, x, y, z, parent) {
    const m = new THREE.Mesh(geo, typeof color === 'number' ? lam(color) : color);
    m.position.set(x || 0, y || 0, z || 0);
    (parent || scene).add(m);
    return m;
  }
  function box(w, h, d, color, x, y, z, parent) { return mesh(new THREE.BoxGeometry(w, h, d), color, x, y, z, parent); }
  function cyl(rt, rb, h, color, x, y, z, parent, seg) { return mesh(new THREE.CylinderGeometry(rt, rb, h, seg || 14), color, x, y, z, parent); }

  function noiseTex(rgb, spread, repeat) {
    const size = 128, c = document.createElement('canvas');
    c.width = c.height = size;
    const g = c.getContext('2d'), img = g.createImageData(size, size);
    for (let i = 0; i < size * size; i++) {
      const n = (Math.random() - 0.5) * spread;
      img.data[i * 4] = clamp(rgb[0] + n, 0, 255);
      img.data[i * 4 + 1] = clamp(rgb[1] + n, 0, 255);
      img.data[i * 4 + 2] = clamp(rgb[2] + n * 0.9, 0, 255);
      img.data[i * 4 + 3] = 255;
    }
    g.putImageData(img, 0, 0);
    const t = new THREE.CanvasTexture(c);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(repeat, repeat);
    t.encoding = THREE.sRGBEncoding;
    return t;
  }

  function textSprite(text, opts) {
    opts = opts || {};
    const c = document.createElement('canvas');
    c.width = 512; c.height = 128;
    const g = c.getContext('2d');
    g.font = `800 ${opts.size || 54}px Manrope, Roboto, sans-serif`;
    const w = Math.min(500, g.measureText(text).width + 44);
    g.fillStyle = opts.bg || 'rgba(16,20,26,0.82)';
    const x = (512 - w) / 2;
    g.beginPath();
    if (g.roundRect) g.roundRect(x, 18, w, 92, 22); else g.rect(x, 18, w, 92);
    g.fill();
    g.fillStyle = opts.color || '#ff6b1a';
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.fillText(text, 256, 66);
    const tex = new THREE.CanvasTexture(c);
    tex.encoding = THREE.sRGBEncoding;
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, depthTest: false, transparent: true }));
    s.scale.set(opts.w || 2.6, (opts.w || 2.6) / 4, 1);
    s.renderOrder = 10;
    return s;
  }

  // ground, base, formwork
  const groundTex = noiseTex([112, 100, 84], 38, 26);
  const ground = mesh(new THREE.PlaneGeometry(92, 72), new THREE.MeshLambertMaterial({ map: groundTex }), 0, 0, 0);
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;

  const road = box(900, 0.02, 9, 0x3b3d40, 0, 0.01, 44);
  box(9, 0.02, 60, 0x3b3d40, 50, 0.01, 14);
  void road;

  // water tank for washing
  const ibc = new THREE.Group();
  box(1.2, 1.1, 1.2, new THREE.MeshLambertMaterial({ color: 0xdfe6ea, transparent: true, opacity: 0.85 }), 0, 0.75, 0, ibc);
  box(1.3, 0.15, 1.3, 0x6c7176, 0, 0.12, 0, ibc);
  cyl(0.05, 0.05, 0.25, 0x333333, 0, 0.35, 0.7, ibc).rotation.x = Math.PI / 2;
  ibc.position.set(POS.ibc.x, 0, POS.ibc.z);
  scene.add(ibc);

  // kebab stand
  const kiosk = new THREE.Group();
  box(3.2, 2.6, 2.6, 0x2f6f6a, 0, 1.3, 0, kiosk);
  box(3.6, 0.12, 1.3, 0xd83a2e, 0, 2.5, 1.8, kiosk);
  box(3.2, 0.9, 0.05, 0x1b2224, 0, 1.6, 1.31, kiosk);
  const kLabel = textSprite('KEBAB & COFFEE', { w: 3.2, color: '#ffd23f' });
  kLabel.position.set(0, 3.3, 0);
  kiosk.add(kLabel);
  kiosk.position.set(POS.kiosk.x, 0, POS.kiosk.z);
  kiosk.rotation.y = 0.5;
  scene.add(kiosk);

  // site office container and the portable toilet, for the atmosphere
  const office = new THREE.Group();
  box(6, 2.6, 2.4, 0x3c6e9e, 0, 1.3, 0, office);
  box(1.2, 0.8, 0.05, 0x1e2a33, 1.2, 1.6, 1.21, office);
  office.position.set(POS.office.x, 0, POS.office.z);
  office.rotation.y = 0.1;
  scene.add(office);
  const loo = new THREE.Group();
  box(1.1, 2.2, 1.1, 0x2c6ad6, 0, 1.1, 0, loo);
  box(1.2, 0.08, 1.2, 0x1d4a99, 0, 2.24, 0, loo);
  loo.position.set(POS.loo.x, 0, POS.loo.z);
  scene.add(loo);

  // fence, with a gap for the gate on the east
  (function fence() {
    const posts = [];
    for (let x = -44; x <= 44; x += 3) { posts.push([x, -34], [x, 34]); }
    for (let z = -34; z <= 34; z += 3) { if (Math.abs(z) > 6) posts.push([44, z]); posts.push([-44, z]); }
    const geo = new THREE.BoxGeometry(0.08, 2, 0.08);
    const im = new THREE.InstancedMesh(geo, lam(0x8a9096), posts.length);
    const m4 = new THREE.Matrix4();
    posts.forEach(([x, z], k) => { m4.makeTranslation(x, 1, z); im.setMatrixAt(k, m4); });
    scene.add(im);
    const mesh2 = new THREE.MeshBasicMaterial({ color: 0x9aa1a7, transparent: true, opacity: 0.18, side: THREE.DoubleSide });
    box(88, 1.8, 0.02, mesh2, 0, 1.0, -34); box(88, 1.8, 0.02, mesh2, 0, 1.0, 34);
    box(0.02, 1.8, 68, mesh2, -44, 1.0, 0);
    box(0.02, 1.8, 28, mesh2, 44, 1.0, -20); box(0.02, 1.8, 28, mesh2, 44, 1.0, 20);
  })();

  // a work light on a pole by the van, for the dark end of the day
  cyl(0.07, 0.09, 8, 0x5a5f64, -21, 4, 3);
  const lamp = box(0.8, 0.35, 0.5, 0xfff3d6, -21, 8, 3);
  lamp.material = new THREE.MeshBasicMaterial({ color: 0x777777 });

  // the laser on its tripod — shown once it is set up
  const tripod = new THREE.Group();
  [0, 2.1, 4.2].forEach((a) => {
    const leg = cyl(0.02, 0.02, 1.5, 0xd8b23a, Math.cos(a) * 0.25, 0.7, Math.sin(a) * 0.25, tripod);
    leg.rotation.z = Math.cos(a) * 0.3;
    leg.rotation.x = -Math.sin(a) * 0.3;
  });
  const laserHead = new THREE.Group();
  box(0.22, 0.2, 0.22, 0xd84a2a, 0, 0, 0, laserHead);
  const beam = new THREE.Mesh(new THREE.BoxGeometry(14, 0.01, 0.01), new THREE.MeshBasicMaterial({ color: 0xff3b30, transparent: true, opacity: 0.55 }));
  beam.position.x = 7;
  laserHead.add(beam);
  laserHead.position.y = 1.5;
  tripod.add(laserHead);
  tripod.position.set(POS.tripod.x, 0, POS.tripod.z);
  tripod.visible = false;
  scene.add(tripod);

  // ------------------------------------------------------------------ vehicles
  /** A side profile, extruded to a width and softened at the edges: cabs and van bodies. */
  function profile(pts, width, color, parent) {
    const sh = new THREE.Shape();
    sh.moveTo(pts[0][0], pts[0][1]);
    for (let k = 1; k < pts.length; k++) sh.lineTo(pts[k][0], pts[k][1]);
    sh.closePath();
    const geo = new THREE.ExtrudeGeometry(sh, { depth: width, bevelEnabled: true, bevelThickness: 0.05, bevelSize: 0.05, bevelSegments: 2, curveSegments: 4 });
    geo.translate(0, 0, -width / 2);
    return mesh(geo, color, 0, 0, 0, parent);
  }
  const GLASS = new THREE.MeshLambertMaterial({ color: 0x1d2a35 });
  function wheel(parent, x, z, r, w) {
    const tyre = cyl(r, r, w, 0x1c1d20, x, r, z, parent, 18);
    tyre.rotation.x = Math.PI / 2;
    const hub = cyl(r * 0.52, r * 0.52, w + 0.02, 0x9ea3a8, x, r, z, parent, 12);
    hub.rotation.x = Math.PI / 2;
    const nut = cyl(r * 0.16, r * 0.16, w + 0.05, 0x5a5f64, x, r, z, parent, 8);
    nut.rotation.x = Math.PI / 2;
  }
  /** A glass panel laid on a slope from (x0, y0) to (x1, y1) across the width. */
  function glassSlope(parent, x0, y0, x1, y1, width, out) {
    const len = Math.hypot(x1 - x0, y1 - y0), a = Math.atan2(y1 - y0, x1 - x0);
    const g = box(len, 0.03, width, GLASS, (x0 + x1) / 2 + Math.sin(a) * -out, (y0 + y1) / 2 + Math.cos(a) * out, 0, parent);
    g.rotation.z = a;
    return g;
  }

  // the van: a panel van, windscreen raked, orange stripe, ladder on the roof
  const van = new THREE.Group();
  profile([[-2.6, 0.45], [3.25, 0.45], [3.3, 1.05], [2.9, 1.38], [2.0, 2.28], [1.7, 2.45], [-2.45, 2.45], [-2.6, 2.3]], 1.95, 0xe9e7e2, van);
  glassSlope(van, 2.92, 1.4, 2.0, 2.28, 1.8, 0.035);
  box(0.95, 0.55, 2.08, GLASS, 1.45, 1.9, 0, van);
  box(5.75, 0.16, 2.07, 0xff6b1a, 0.3, 1.12, 0, van);
  box(0.16, 0.3, 2.1, 0x2a2d31, 3.32, 0.62, 0, van);
  box(0.16, 0.3, 2.1, 0x2a2d31, -2.66, 0.62, 0, van);
  box(0.05, 0.28, 1.1, 0x2a2d31, 3.33, 0.92, 0, van);
  [-1, 1].forEach((s) => box(0.14, 0.2, 0.08, 0x2a2d31, 2.05, 1.72, s * 1.1, van));
  box(0.02, 1.6, 0.02, 0x9aa1a7, -2.63, 1.4, 0, van);       // the rear doors' seam
  box(3.6, 0.05, 1.5, 0x5a5f64, -0.4, 2.56, 0, van);
  [-0.45, 0.45].forEach((z) => box(3.4, 0.05, 0.06, 0xc0c4c8, -0.4, 2.63, z, van));
  for (let x = -1.9; x <= 1.1; x += 0.4) box(0.04, 0.04, 0.9, 0xc0c4c8, x, 2.64, 0, van);
  [[-1.7, 1.0], [-1.7, -1.0], [2.3, 1.0], [2.3, -1.0]].forEach(([x, z]) => wheel(van, x, z, 0.38, 0.26));
  const vanLabel = textSprite('THE VAN', { w: 2.2 });
  vanLabel.position.set(0, 3.2, 0);
  van.add(vanLabel);
  van.position.set(POS.van.x, 0, POS.van.z);
  van.rotation.y = -0.25;
  scene.add(van);

  // a truck cab, facing -x: raked windscreen, grille, bumper, mirrors, steps
  function cab(parent, color) {
    profile([[-4.7, 0.8], [-2.35, 0.8], [-2.35, 3.2], [-4.35, 3.2], [-4.55, 3.0], [-4.7, 2.1]], 2.3, color, parent);
    glassSlope(parent, -4.69, 2.12, -4.55, 3.0, 2.1, -0.04);
    box(1.15, 0.7, 2.42, GLASS, -3.85, 2.55, 0, parent);
    box(0.06, 0.62, 1.5, 0x2a2d31, -4.76, 1.45, 0, parent);
    box(0.22, 0.32, 2.45, 0x5a5f64, -4.8, 0.82, 0, parent);
    [-1, 1].forEach((s) => {
      box(0.06, 0.55, 0.1, 0x2a2d31, -4.62, 2.55, s * 1.36, parent);
      box(0.3, 0.06, 0.4, 0x5a5f64, -3.5, 0.72, s * 1.18, parent);
    });
    box(9.2, 0.35, 1.0, 0x2a2d31, 0, 0.95, 0, parent);
    const tank = cyl(0.26, 0.26, 1.0, 0xb9bec3, -1.7, 1.0, 1.05, parent, 12);
    tank.rotation.z = Math.PI / 2;
  }
  function truckWheels(parent) {
    [-3.3, 1.6, 3.0].forEach((x) => [1.1, -1.1].forEach((z) => wheel(parent, x, z, 0.5, 0.36)));
    [1.6, 3.0].forEach((x) => [1.18, -1.18].forEach((z) => box(0.9, 0.05, 0.42, 0x2a2d31, x, 1.08, z, parent)));
  }

  // the pump and the mixer, off site until they come
  const pump = new THREE.Group();
  cab(pump, 0xf2f0ea);
  truckWheels(pump);
  box(4.8, 1.4, 2.3, 0xf2b705, 0.6, 1.85, 0, pump);
  box(4.6, 0.1, 2.35, 0x2a2d31, 0.6, 2.6, 0, pump);
  const hopper = cyl(0.95, 0.5, 0.75, 0x444a50, 3.8, 1.75, 0, pump, 4);
  hopper.rotation.y = Math.PI / 4;
  box(0.9, 0.12, 0.3, 0x2a2d31, 3.6, 0.7, 1.45, pump);
  box(0.9, 0.12, 0.3, 0x2a2d31, 3.6, 0.7, -1.45, pump);
  [-0.4, 0, 0.4].forEach((y) => { const r = cyl(0.07, 0.07, 4.2, 0x6d7278, 0.6, 2.05 + y, 1.22, pump, 8); r.rotation.z = Math.PI / 2; });
  cyl(0.07, 0.07, 1.3, 0x2a2d31, -2.2, 2.9, -1.0, pump, 8);
  const pumpLabel = textSprite('LINE PUMP', { w: 2.4, color: '#ffd23f' });
  pumpLabel.position.set(0.6, 3.9, 0);
  pump.add(pumpLabel);
  pump.position.set(70, 0, POS.pump.z);
  pump.visible = false;
  scene.add(pump);
  const boomLabel = textSprite('BOOM PUMP', { w: 2.4, color: '#ffd23f' });
  boomLabel.position.set(0.6, 4.6, 0);
  boomLabel.visible = false;
  pump.add(boomLabel);
  // on a boom day the pump brings its own pipe: a turret at the back of the truck, three 9.5 m
  // sections folded over the cab, and legs that swing out to hold it all up
  const BOOM_L = 9.5, TURRET = new THREE.Vector3(3.0, 3.2, 0);
  const boomParts = new THREE.Group();
  pump.add(boomParts);
  cyl(0.55, 0.65, 0.6, 0x2a2d31, TURRET.x, 2.9, 0, boomParts, 14);
  const outriggers = [[-3.2, 1], [-3.2, -1], [2.6, 1], [2.6, -1]].map(([x, sd]) => {
    const g = new THREE.Group();
    g.position.set(x, 0.75, sd * 1.2);
    const beam = box(0.22, 0.22, 1, 0xf2b705, 0, 0, sd * 0.5, g);
    const leg = box(0.16, 0.8, 0.16, 0x2a2d31, 0, -0.35, sd * 1, g);
    boomParts.add(g);
    return { g, beam, leg, sd };
  });
  const boomSecs = [0, 1, 2].map((k) => { const m = box(0.42 - k * 0.07, 1, 0.42 - k * 0.07, 0xf2b705, 0, 0, 0, scene); m.visible = false; m.castShadow = true; return m; });
  const boomTip = new THREE.Vector3();

  // the mixer: a pear-shaped drum on a tilt, with spiral bands so you can see it turn — one way to
  // keep the load mixed on the road, the other way to bring it up and out down the chute
  const mixer = new THREE.Group();
  cab(mixer, 0xe0e3e6);
  truckWheels(mixer);
  const drumTex = (function () {
    const c = document.createElement('canvas');
    c.width = 256; c.height = 128;
    const g = c.getContext('2d');
    g.fillStyle = '#ff6b1a'; g.fillRect(0, 0, 256, 128);
    g.strokeStyle = '#f2f0ea'; g.lineWidth = 16;
    for (let k = -2; k < 4; k++) { g.beginPath(); g.moveTo(k * 90, 0); g.lineTo(k * 90 + 128, 128); g.stroke(); }
    g.fillStyle = 'rgba(0,0,0,0.12)';
    for (let y = 0; y < 128; y += 32) g.fillRect(0, y, 256, 2);
    const t = new THREE.CanvasTexture(c);
    t.encoding = THREE.sRGBEncoding;
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    return t;
  })();
  const drumPivot = new THREE.Group();
  drumPivot.position.set(0.9, 2.55, 0);
  drumPivot.rotation.z = 0.2;
  const drumSpin = new THREE.Group();
  drumPivot.add(drumSpin);
  const drumProfile = [[0.3, -2.45], [0.85, -2.2], [1.12, -1.4], [1.18, -0.4], [1.08, 0.7], [0.8, 1.7], [0.48, 2.35], [0.46, 2.4]].map(([r, y]) => new THREE.Vector2(r, y));
  const drum = new THREE.Mesh(new THREE.LatheGeometry(drumProfile, 26), new THREE.MeshLambertMaterial({ map: drumTex }));
  drum.rotation.z = -Math.PI / 2;
  drumSpin.add(drum);
  mixer.add(drumPivot);
  box(0.4, 1.4, 0.25, 0x2a2d31, -1.3, 1.7, 0.75, mixer);
  box(0.4, 1.4, 0.25, 0x2a2d31, -1.3, 1.7, -0.75, mixer);
  box(0.35, 0.9, 1.6, 0x2a2d31, 3.2, 1.55, 0, mixer);
  const chute = box(1.3, 0.06, 0.42, 0x8e9398, 4.0, 1.95, 0, mixer);
  chute.rotation.z = -0.45;
  const mhop = cyl(0.55, 0.22, 0.55, 0x5a5f64, 3.65, 3.25, 0, mixer, 4);
  mhop.rotation.y = Math.PI / 4;
  const wtank = cyl(0.33, 0.33, 1.8, 0x3c6e9e, -1.95, 2.3, 0, mixer, 12);
  wtank.rotation.x = Math.PI / 2;
  mixer.position.set(80, 0, POS.mixer.z);
  mixer.visible = false;
  scene.add(mixer);

  const pipeGroup = new THREE.Group();
  scene.add(pipeGroup);
  const pipeMeshes = [];
  const pile = new THREE.Group();
  for (let k = 0; k < 6; k++) {
    const p = cyl(0.07, 0.07, 3, 0x6d7278, 0, 0.1 + (k % 3) * 0.15, (Math.floor(k / 3) - 0.5) * 0.18 + (k % 3) * 0.05, pile, 10);
    p.rotation.z = Math.PI / 2;
  }
  pile.position.set(POS.pile.x, 0, POS.pile.z);
  pile.visible = false;
  scene.add(pile);

  function pipeBetween(a, b, color, radius) {
    const len = hyp(a.x, a.z, b.x, b.z);
    const m = cyl(radius || 0.07, radius || 0.07, len, color || 0x6d7278, (a.x + b.x) / 2, 0.12, (a.z + b.z) / 2, pipeGroup, 10);
    m.rotation.z = Math.PI / 2;
    m.rotation.y = -Math.atan2(b.z - a.z, b.x - a.x);
    const ring = cyl(0.1, 0.1, 0.12, 0x2f3338, b.x, 0.12, b.z, pipeGroup, 10);
    ring.rotation.z = Math.PI / 2;
    ring.rotation.y = m.rotation.y;
    return [m, ring];
  }

  // people and a dog
  const SKINS = [0xe0b390, 0xc68e62, 0x8d5a3b, 0xf0c8a0, 0xb07850];
  const HAIR = [0x2b2622, 0x5a3b22, 0x8a6a3a, 0xb9b2a6, 0x1a1a1a];
  function capsule(r, len, color, x, y, z, parent) { return mesh(new THREE.CapsuleGeometry(r, len, 4, 8), color, x, y, z, parent); }
  /**
   * A person: rounded limbs on hip and shoulder pivots, hands, boots, a face that can look at you,
   * and a hard hat, a cap or hair; some in hi-vis. Faces +z.
   */
  function makePerson(opts) {
    opts = opts || {};
    const g = new THREE.Group();
    const body = new THREE.Group();
    g.add(body);
    const shirt = opts.shirt || pick([0x3b5b8c, 0x8c3b3b, 0x4e7a44, 0x5a4f7a, 0xc9c3b8, 0x2f3540, 0x7a5a3a]);
    const pants = opts.pants || pick([0x2f3540, 0x3a3226, 0x23262b, 0x4a5a70, 0x5b5a52]);
    const skin = opts.skin || pick(SKINS);
    const leg = (x) => {
      const p = new THREE.Group();
      p.position.set(x, 0.9, 0);
      capsule(0.075, 0.62, pants, 0, -0.42, 0, p);
      box(0.12, 0.08, 0.27, 0x22211f, 0, -0.86, 0.04, p);
      g.add(p);
      return p;
    };
    const legL = leg(-0.1), legR = leg(0.1);
    box(0.34, 0.14, 0.2, pants, 0, 0.93, 0, body);
    const torso = cyl(0.2, 0.16, 0.6, shirt, 0, 1.24, 0, body, 12);
    torso.scale.z = 0.65;
    if (opts.vest) {
      const v = cyl(0.206, 0.166, 0.46, opts.vest, 0, 1.22, 0, body, 12);
      v.scale.z = 0.68;
      [1.12, 1.3].forEach((y) => { const st = cyl(0.209, 0.19, 0.035, 0xd9dde0, 0, y, 0, body, 12); st.scale.z = 0.69; });
    }
    const sh = mesh(new THREE.SphereGeometry(0.21, 12, 8), opts.vest || shirt, 0, 1.5, 0, body);
    sh.scale.set(1, 0.42, 0.62);
    const arm = (x) => {
      const p = new THREE.Group();
      p.position.set(x, 1.5, 0);
      capsule(0.055, 0.46, shirt, 0, -0.28, 0, p);
      mesh(new THREE.SphereGeometry(0.055, 8, 6), skin, 0, -0.6, 0, p);
      body.add(p);
      return p;
    };
    const armL = arm(-0.25), armR = arm(0.25);
    cyl(0.05, 0.055, 0.1, skin, 0, 1.6, 0, body, 8);
    const head = new THREE.Group();
    head.position.set(0, 1.74, 0);
    body.add(head);
    mesh(new THREE.SphereGeometry(0.12, 14, 12), skin, 0, 0, 0, head).scale.set(0.92, 1.05, 1);
    [-1, 1].forEach((s) => box(0.022, 0.022, 0.01, 0x111111, s * 0.04, 0.02, 0.112, head));
    box(0.022, 0.035, 0.03, skin, 0, -0.012, 0.12, head);
    const hat = opts.hat === undefined ? pick(['hard', 'cap', 'hair', 'hair']) : opts.hat;
    if (hat === 'hard') {
      const hc = opts.hatColor || pick([0xf2b705, 0xf2f0ea, 0xff6b1a, 0x2c6ad6]);
      mesh(new THREE.SphereGeometry(0.138, 14, 8, 0, Math.PI * 2, 0, Math.PI / 2), hc, 0, 0.03, 0, head);
      cyl(0.165, 0.165, 0.014, hc, 0, 0.035, 0.025, head, 18);
    } else if (hat === 'cap') {
      const cc = pick([0x2f3540, 0x8c3b3b, 0x4e7a44, 0xc9c3b8]);
      mesh(new THREE.SphereGeometry(0.128, 14, 8, 0, Math.PI * 2, 0, Math.PI / 2), cc, 0, 0.02, 0, head);
      box(0.15, 0.012, 0.1, cc, 0, 0.03, 0.14, head);
    } else {
      mesh(new THREE.SphereGeometry(0.126, 12, 8, 0, Math.PI * 2, 0, Math.PI / 2.1), pick(HAIR), 0, 0.015, -0.012, head);
    }
    // a ponytail, under whatever is on top
    if (opts.g === 'f') capsule(0.042, 0.15, pick(HAIR), 0, -0.07, -0.125, head).rotation.x = 0.35;
    g.userData = { legL, legR, armL, armR, head, body, phase: Math.random() * 6 };
    return g;
  }
  /** A dog: capsule body, a proper head with a snout and ears, a tail that wags. Faces +x. A cat is
   *  the same animal with smaller bones, a flat face, tall ears and a tail held up like a question. */
  function makeDog(cat) {
    const g = new THREE.Group();
    const c = cat ? pick([0x7a7d80, 0xd98b3a, 0x1f1f22, 0xe8e2d6]) : pick([0x8a5a2b, 0x2b2622, 0xd9c6a1, 0x6b6560, 0xa0522d]);
    const size = cat ? rnd(0.5, 0.58) : rnd(0.8, 1.15);
    const body = new THREE.Group();
    body.scale.setScalar(size);
    g.add(body);
    capsule(0.13, 0.42, c, 0, 0.45, 0, body).rotation.z = Math.PI / 2;
    capsule(0.07, 0.12, c, 0.3, 0.58, 0, body).rotation.z = -0.9;
    const head = new THREE.Group();
    head.position.set(0.38, 0.68, 0);
    body.add(head);
    mesh(new THREE.SphereGeometry(cat ? 0.12 : 0.1, 12, 10), c, 0, 0, 0, head);
    if (cat) mesh(new THREE.SphereGeometry(0.02, 6, 6), 0xd98a9a, 0.12, -0.01, 0, head);
    else {
      capsule(0.045, 0.08, c, 0.1, -0.03, 0, head).rotation.z = Math.PI / 2;
      mesh(new THREE.SphereGeometry(0.025, 6, 6), 0x111111, 0.175, -0.02, 0, head);
    }
    [-1, 1].forEach((s) => {
      const ear = mesh(new THREE.ConeGeometry(cat ? 0.05 : 0.035, cat ? 0.12 : 0.09, cat ? 4 : 6), c, -0.02, cat ? 0.11 : 0.1, s * (cat ? 0.07 : 0.06), head);
      ear.rotation.x = s * (cat ? 0.25 : 0.35);
      box(0.014, cat ? 0.022 : 0.014, 0.014, cat ? 0x9bd14a : 0x111111, cat ? 0.1 : 0.075, 0.03, s * 0.045, head);
    });
    const tail = new THREE.Group();
    tail.position.set(-0.3, 0.52, 0);
    body.add(tail);
    capsule(0.025, cat ? 0.42 : 0.2, c, 0, cat ? 0.22 : 0.12, 0, tail);
    tail.rotation.z = cat ? 0.15 : 0.7;
    const legs = [[0.2, 0.08], [0.2, -0.08], [-0.2, 0.08], [-0.2, -0.08]].map(([x, z]) => {
      const p = new THREE.Group();
      p.position.set(x, 0.38, z);
      capsule(0.035, 0.26, c, 0, -0.19, 0, p);
      body.add(p);
      return p;
    });
    g.userData = { legs, tail, head, body, phase: 0, size };
    return g;
  }
  const pumpGuy = makePerson({ shirt: 0x2f3540, vest: 0xff7a1a, hat: 'hard', hatColor: 0xf2f0ea });
  pumpGuy.visible = false;
  scene.add(pumpGuy);
  const mixGuy = makePerson({ shirt: 0x3b5b8c, vest: 0xd4f53c, hat: 'cap' });
  mixGuy.visible = false;
  scene.add(mixGuy);

  // ------------------------------------------------------------------ the world beyond the fence
  // sky: a dome shaded from the horizon up, with the sun, the moon and the stars on it
  const skyUni = { top: { value: new THREE.Color() }, bottom: { value: new THREE.Color() } };
  const skyDome = new THREE.Mesh(new THREE.SphereGeometry(360, 24, 14), new THREE.ShaderMaterial({
    uniforms: skyUni, side: THREE.BackSide, depthWrite: false, fog: false,
    vertexShader: 'varying vec3 vP; void main() { vP = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }',
    fragmentShader: 'uniform vec3 top; uniform vec3 bottom; varying vec3 vP; void main() { float h = clamp(vP.y * 1.8 + 0.04, 0.0, 1.0); gl_FragColor = linearToOutputTexel(vec4(mix(bottom, top, pow(h, 0.8)), 1.0)); }',
  }));
  skyDome.renderOrder = -1;
  scene.add(skyDome);
  function glowTex(inner, outer) {
    const c = document.createElement('canvas');
    c.width = c.height = 128;
    const g = c.getContext('2d');
    const grad = g.createRadialGradient(64, 64, 0, 64, 64, 64);
    grad.addColorStop(0, inner); grad.addColorStop(0.22, inner); grad.addColorStop(0.3, outer); grad.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, 128, 128);
    const t = new THREE.CanvasTexture(c);
    t.encoding = THREE.sRGBEncoding;
    return t;
  }
  const sunDisc = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex('rgba(255,248,225,1)', 'rgba(255,214,150,0.35)'), fog: false, depthWrite: false, transparent: true }));
  sunDisc.scale.setScalar(70);
  scene.add(sunDisc);
  const moonDisc = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex('rgba(236,240,250,1)', 'rgba(180,200,240,0.18)'), fog: false, depthWrite: false, transparent: true }));
  moonDisc.scale.setScalar(34);
  scene.add(moonDisc);
  const stars = (function () {
    const n = 700, pos = new Float32Array(n * 3);
    for (let k = 0; k < n; k++) {
      const a = Math.random() * Math.PI * 2, y = Math.random() * 0.95 + 0.05, r = Math.sqrt(1 - y * y);
      pos[k * 3] = Math.cos(a) * r * 330; pos[k * 3 + 1] = y * 330; pos[k * 3 + 2] = Math.sin(a) * r * 330;
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    const p = new THREE.Points(geo, new THREE.PointsMaterial({ color: 0xffffff, size: 1.7, sizeAttenuation: false, transparent: true, opacity: 0, fog: false, depthWrite: false }));
    scene.add(p);
    return p;
  })();

  // shadows from the sun, in a square that follows you round the site
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  Object.assign(sun.shadow.camera, { left: -26, right: 26, top: 26, bottom: -26, near: 1, far: 200 });
  sun.shadow.bias = -0.0006;
  sun.shadow.normalBias = 0.03;
  scene.add(sun.target);

  // grass round the yard, a tree line, the town behind it, and somebody else's crane
  const grass = mesh(new THREE.PlaneGeometry(900, 900), new THREE.MeshLambertMaterial({ map: noiseTex([74, 92, 52], 34, 180) }), 0, -0.02, 0);
  grass.rotation.x = -Math.PI / 2;
  grass.receiveShadow = true;
  (function trees() {
    const spots = [];
    for (let k = 0; k < 900 && spots.length < 190; k++) {
      const a = Math.random() * Math.PI * 2, r = rnd(52, 120);
      const x = Math.cos(a) * r * 1.2, z = Math.sin(a) * r;
      if (Math.abs(z - 44) < 9 || (Math.abs(x - 50) < 8 && z > -20 && z < 44)) continue;   // the road
      spots.push([x, z, rnd(0.7, 1.5), chance(0.65)]);
    }
    const pineGeo = new THREE.ConeGeometry(1, 1, 7); pineGeo.translate(0, 0.5, 0);
    const leafGeo = new THREE.IcosahedronGeometry(1, 0);
    const trunkGeo = new THREE.CylinderGeometry(0.12, 0.18, 1, 5); trunkGeo.translate(0, 0.5, 0);
    const pines = spots.filter((s) => s[3]), leafy = spots.filter((s) => !s[3]);
    const pineM = new THREE.InstancedMesh(pineGeo, new THREE.MeshLambertMaterial({ color: 0xffffff }), pines.length);
    const leafM = new THREE.InstancedMesh(leafGeo, new THREE.MeshLambertMaterial({ color: 0xffffff }), leafy.length);
    const trunkM = new THREE.InstancedMesh(trunkGeo, lam(0x5a4632), spots.length);
    const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), sc = new THREE.Vector3(), ps = new THREE.Vector3(), col = new THREE.Color();
    pines.forEach(([x, z, s], k) => {
      m4.compose(ps.set(x, 1.2 * s, z), q, sc.set(2.2 * s, 9 * s, 2.2 * s)); pineM.setMatrixAt(k, m4);
      pineM.setColorAt(k, col.setHSL(0.33 + rnd(-0.03, 0.03), 0.38, rnd(0.16, 0.24)));
    });
    leafy.forEach(([x, z, s], k) => {
      m4.compose(ps.set(x, 4.2 * s, z), q.setFromEuler(new THREE.Euler(rnd(0, 3), rnd(0, 3), 0)), sc.set(2.6 * s, 2.9 * s, 2.6 * s)); leafM.setMatrixAt(k, m4);
      leafM.setColorAt(k, col.setHSL(0.24 + rnd(-0.04, 0.05), 0.42, rnd(0.25, 0.34)));
    });
    q.identity();
    spots.forEach(([x, z, s, pine], k) => { m4.compose(ps.set(x, 0, z), q, sc.set(s, (pine ? 1.4 : 3) * s, s)); trunkM.setMatrixAt(k, m4); });
    [pineM, leafM, trunkM].forEach((m) => { m.castShadow = true; scene.add(m); });
  })();
  // the town: blocks with windows that light up when it gets dark
  const winTex = (function () {
    const c = document.createElement('canvas');
    c.width = 64; c.height = 128;
    const g = c.getContext('2d');
    g.fillStyle = '#000'; g.fillRect(0, 0, 64, 128);
    for (let y = 6; y < 128; y += 12) for (let x = 5; x < 64; x += 12) {
      if (Math.random() < 0.45) { g.fillStyle = Math.random() < 0.8 ? '#ffd9a0' : '#bcd8ff'; g.fillRect(x, y, 6, 6); }
    }
    const t = new THREE.CanvasTexture(c);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.encoding = THREE.sRGBEncoding;
    return t;
  })();
  const townMats = [0x9aa0a6, 0xb9ae9c, 0x8c96a3, 0xc7c2b8].map((c) => new THREE.MeshLambertMaterial({ color: c, emissive: 0xffffff, emissiveMap: winTex, emissiveIntensity: 0 }));
  for (let k = 0; k < 26; k++) {
    const a = rnd(-Math.PI, Math.PI), r = rnd(150, 210);
    const w = rnd(12, 30), h = rnd(10, 42), d = rnd(12, 24);
    const b = box(w, h, d, townMats[k % townMats.length], Math.cos(a) * r, h / 2, Math.sin(a) * r);
    b.rotation.y = -a;
  }
  // a tower crane on the next lot, turning now and then
  const crane = new THREE.Group();
  const latTex = (function () {
    const c = document.createElement('canvas');
    c.width = c.height = 32;
    const g = c.getContext('2d');
    g.strokeStyle = '#fff'; g.lineWidth = 3;
    g.strokeRect(1.5, 1.5, 29, 29);
    g.beginPath(); g.moveTo(0, 0); g.lineTo(32, 32); g.stroke();
    const t = new THREE.CanvasTexture(c);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    return t;
  })();
  const latMat = (rx, ry) => { const t = latTex.clone(); t.needsUpdate = true; t.repeat.set(rx, ry); return new THREE.MeshLambertMaterial({ color: 0xf2b705, map: t, alphaTest: 0.5, transparent: false, side: THREE.DoubleSide, alphaMap: t }); };
  box(1.8, 44, 1.8, latMat(1, 24), 0, 22, 0, crane);
  const jib = new THREE.Group();
  jib.position.y = 44;
  box(52, 1.6, 1.6, latMat(30, 1), 10, 0.8, 0, jib);
  box(4, 2.4, 2.4, 0x6c7176, -13, 0.4, 0, jib);
  box(2.2, 2.2, 2.2, 0xe9e7e2, 1.5, -0.6, 1.8, jib);
  const hookLine = cyl(0.03, 0.03, 20, 0x222222, 26, -10, 0, jib, 4);
  void hookLine;
  crane.add(jib);
  crane.position.set(-70, 0, -64);
  scene.add(crane);

  // lights on the machines, for the dark end of the morning
  const headMat = new THREE.MeshBasicMaterial({ color: 0x555555 });
  const beamMat = new THREE.MeshBasicMaterial({ color: 0xfff1c9, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide });
  function headlights(group, x, y, zs) {
    zs.forEach((z) => {
      box(0.08, 0.18, 0.3, headMat, x, y, z, group);
      const b = new THREE.Mesh(new THREE.ConeGeometry(1.6, 9, 12, 1, true), beamMat);
      b.rotation.z = x < 0 ? -Math.PI / 2 - 0.08 : Math.PI / 2 + 0.08;
      b.position.set(x + (x < 0 ? -4.5 : 4.5), y - 0.35, z);
      group.add(b);
    });
  }
  headlights(van, 3.28, 0.98, [-0.72, 0.72]);
  headlights(pump, -4.74, 1.25, [-0.9, 0.9]);
  headlights(mixer, -4.74, 1.25, [-0.9, 0.9]);

  // particles: splashes, spray, slurry off the machine, rain, exhaust
  const PMAX = 900;
  const pPos = new Float32Array(PMAX * 3), pCol = new Float32Array(PMAX * 3), pSize = new Float32Array(PMAX);
  const pGeo = new THREE.BufferGeometry();
  pGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3));
  pGeo.setAttribute('pcolor', new THREE.BufferAttribute(pCol, 3));
  pGeo.setAttribute('psize', new THREE.BufferAttribute(pSize, 1));
  const pUni = { scale: { value: 600 } };
  const parts = new THREE.Points(pGeo, new THREE.ShaderMaterial({
    uniforms: pUni, depthWrite: false, transparent: true,
    vertexShader: 'attribute vec3 pcolor; attribute float psize; uniform float scale; varying vec3 vC; void main() { vC = pcolor; vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_PointSize = psize * scale / max(0.1, -mv.z); gl_Position = projectionMatrix * mv; }',
    fragmentShader: 'varying vec3 vC; void main() { vec2 d = gl_PointCoord - 0.5; float r = dot(d, d); if (r > 0.25) discard; gl_FragColor = linearToOutputTexel(vec4(vC * (1.0 - r * 1.2), 1.0 - r * 2.0)); }',
  }));
  parts.frustumCulled = false;
  scene.add(parts);
  const pv = Array.from({ length: PMAX }, () => ({ life: 0, vx: 0, vy: 0, vz: 0, g: 0, size: 0 }));
  let pNext = 0;
  const pc = new THREE.Color();
  /** One particle: where, which way, how long, what colour, how big (metres), how it falls. */
  function emit(x, y, z, vx, vy, vz, life, color, size, gravity) {
    const k = pNext;
    pNext = (pNext + 1) % PMAX;
    const p = pv[k];
    p.life = life; p.vx = vx; p.vy = vy; p.vz = vz; p.g = gravity === undefined ? -9.8 : gravity; p.size = size;
    pPos[k * 3] = x; pPos[k * 3 + 1] = y; pPos[k * 3 + 2] = z;
    pc.setHex(color).convertSRGBToLinear();
    pCol[k * 3] = pc.r; pCol[k * 3 + 1] = pc.g; pCol[k * 3 + 2] = pc.b;
    pSize[k] = size;
  }
  function updateParticles(dt) {
    for (let k = 0; k < PMAX; k++) {
      const p = pv[k];
      if (p.life <= 0) continue;
      p.life -= dt;
      if (p.life <= 0) { pSize[k] = 0; continue; }
      p.vy += p.g * dt;
      pPos[k * 3] += p.vx * dt; pPos[k * 3 + 1] += p.vy * dt; pPos[k * 3 + 2] += p.vz * dt;
      if (p.g > 0) pSize[k] = p.size * (1 + (1.5 - p.life));          // smoke grows as it rises
      if (pPos[k * 3 + 1] < 0 && p.g < 0) { p.life = 0; pSize[k] = 0; }
    }
    pGeo.attributes.position.needsUpdate = true;
    pGeo.attributes.pcolor.needsUpdate = true;
    pGeo.attributes.psize.needsUpdate = true;
  }

  // ------------------------------------------------------------------ the slab surface
  // One smooth surface over the whole pour: its height from the concrete in each square metre,
  // its face a canvas that gets painted as the day goes — fresh concrete, float strokes, the
  // rough circles of the pans, the closed shine of the blades, and everybody's footprints.
  const SEG = 4;
  const slabGeo = new THREE.PlaneGeometry(NX, NZ, NX * SEG, NZ * SEG);
  slabGeo.rotateX(-Math.PI / 2);
  slabGeo.translate((SLAB.x0 + SLAB.x1) / 2, 0, (SLAB.z0 + SLAB.z1) / 2);
  const slabPos = slabGeo.attributes.position;
  const slabCol = new Float32Array(slabPos.count * 3).fill(1);
  slabGeo.setAttribute('color', new THREE.BufferAttribute(slabCol, 3));
  const PPM = 48;
  const K = PPM / 80;                       // marks and strokes were drawn for 80 px a metre
  const surfCanvas = document.createElement('canvas');
  surfCanvas.width = NX * PPM;
  surfCanvas.height = NZ * PPM;
  const surf = surfCanvas.getContext('2d');
  const surfTex = new THREE.CanvasTexture(surfCanvas);
  surfTex.encoding = THREE.sRGBEncoding;
  surfTex.anisotropy = Math.min(4, renderer.capabilities.getMaxAnisotropy());
  const slabMat = new THREE.MeshPhongMaterial({ map: surfTex, vertexColors: true, shininess: 60, specular: 0x444444 });
  const slabMesh = new THREE.Mesh(slabGeo, slabMat);
  slabMesh.receiveShadow = true;
  scene.add(slabMesh);
  let surfDirty = true;
  let surfWait = 0;
  const tmpC = new THREE.Color();

  const speckle = (function () {
    const c = document.createElement('canvas');
    c.width = c.height = 128;
    const g = c.getContext('2d');
    const img = g.createImageData(128, 128);
    for (let k = 0; k < img.data.length; k += 4) {
      const n = 214 + (Math.random() - 0.5) * 30;
      img.data[k] = n; img.data[k + 1] = n; img.data[k + 2] = n - 3; img.data[k + 3] = 255;
    }
    g.putImageData(img, 0, 0);
    for (let k = 0; k < 90; k++) {
      g.fillStyle = Math.random() < 0.5 ? 'rgba(120,120,116,0.35)' : 'rgba(250,250,246,0.35)';
      g.fillRect(Math.random() * 128, Math.random() * 128, 1 + Math.random() * 2, 1 + Math.random() * 2);
    }
    return surf.createPattern(c, 'repeat');
  })();
  const cx = (x) => (x - SLAB.x0) * PPM;
  const cz = (z) => (z - SLAB.z0) * PPM;
  function freshSurface() {
    surf.globalAlpha = 1;
    surf.fillStyle = speckle;
    surf.fillRect(0, 0, surfCanvas.width, surfCanvas.height);
    surfDirty = true;
  }
  freshSurface();
  function disc(x, z, r, style, alpha) {
    surf.globalAlpha = alpha;
    surf.fillStyle = style;
    surf.beginPath();
    surf.arc(cx(x), cz(z), r * PPM, 0, Math.PI * 2);
    surf.fill();
    surf.globalAlpha = 1;
    surfDirty = true;
  }
  /** New concrete over whatever was there. */
  function paintPour(x, z) { disc(x, z, 0.7, speckle, 0.85); }
  /** A float drawn across: straight, overlapping strokes. */
  function paintFloat(x, z, rot) {
    surf.save();
    surf.translate(cx(x), cz(z));
    surf.rotate(-rot);
    surf.scale(K, K);
    surf.globalAlpha = 0.45;
    surf.fillStyle = speckle;
    surf.fillRect(-36, -10, 72, 20);
    surf.lineWidth = 1.5;
    for (let k = -8; k <= 8; k += 4) {
      surf.globalAlpha = 0.16;
      surf.strokeStyle = k % 8 ? '#fbfbf8' : '#9d9d99';
      surf.beginPath(); surf.moveTo(-34, k); surf.lineTo(34, k + rnd(-1, 1)); surf.stroke();
    }
    surf.restore();
    surf.globalAlpha = 1;
    surfDirty = true;
  }
  /** The float pans: a rough, grainy surface in overlapping circles. */
  function paintPan(x, z, r) {
    r = r || 0.46;
    const X = cx(x), Z = cz(z), R = r * PPM;
    disc(x, z, r, speckle, 0.16);
    surf.lineWidth = 2.2 * K;
    for (let k = 0; k < 6; k++) {
      const a = rnd(0, Math.PI * 2);
      surf.globalAlpha = rnd(0.1, 0.24);
      surf.strokeStyle = chance(0.5) ? '#8f8f8b' : '#f4f4f0';
      surf.beginPath();
      surf.arc(X + rnd(-5, 5) * K, Z + rnd(-5, 5) * K, rnd(0.2, 1) * R, a, a + rnd(0.8, 2.4));
      surf.stroke();
    }
    surf.globalAlpha = 1;
    surfDirty = true;
  }
  /** The blades: closed, smooth and lighter — where the shine comes from. */
  function paintBlade(x, z, r) {
    r = r || 0.46;
    const X = cx(x), Z = cz(z), R = r * PPM;
    disc(x, z, r, '#eeeff0', 0.1);
    surf.lineWidth = 6 * K;
    for (let k = 0; k < 3; k++) {
      const a = rnd(0, Math.PI * 2);
      surf.globalAlpha = 0.09;
      surf.strokeStyle = '#ffffff';
      surf.beginPath();
      surf.arc(X, Z, rnd(0.35, 0.95) * R, a, a + rnd(1.2, 2.8));
      surf.stroke();
    }
    surf.globalAlpha = 1;
    surfDirty = true;
  }
  /** Fibre concrete: little hairs all through the surface. */
  function paintFibres(x, z) {
    surf.strokeStyle = '#6e6a60';
    surf.lineWidth = 1;
    surf.globalAlpha = 0.5;
    for (let k = 0; k < 10; k++) {
      const X = cx(x + rnd(-0.6, 0.6)), Z = cz(z + rnd(-0.6, 0.6)), a = rnd(0, Math.PI), l = rnd(2, 5);
      surf.beginPath(); surf.moveTo(X, Z); surf.lineTo(X + Math.cos(a) * l, Z + Math.sin(a) * l); surf.stroke();
    }
    surf.globalAlpha = 1;
    surfDirty = true;
  }
  /** Too early: the machine digs in and throws up ridges. */
  function paintGouge(x, z, rot) {
    surf.save();
    surf.translate(cx(x), cz(z));
    surf.rotate(rot);
    surf.scale(K, K);
    surf.lineWidth = 5;
    surf.globalAlpha = 0.5;
    surf.strokeStyle = '#55575a';
    surf.beginPath(); surf.arc(0, 0, rnd(10, 26), 0, rnd(1.5, 3)); surf.stroke();
    surf.lineWidth = 2.5;
    surf.globalAlpha = 0.45;
    surf.strokeStyle = '#f0f0ec';
    surf.beginPath(); surf.arc(3, 3, rnd(10, 26), 0.3, rnd(1.5, 3)); surf.stroke();
    surf.restore();
    surf.globalAlpha = 1;
    surfDirty = true;
  }
  /** A footprint, a paw, a behind — as deep as it still is. One that has set stays dark. */
  function drawMark(g, m, set) {
    const d = set ? Math.max(m.depth, 0.6) : m.depth;
    g.save();
    g.translate(cx(m.x), cz(m.z));
    g.rotate(-m.rot);
    g.scale(K, K);
    const a = 0.72 * Math.min(1, d / 0.7);
    g.globalAlpha = a;
    g.fillStyle = '#45474a';
    const ell = (x, y, rx, ry, r) => { g.beginPath(); g.ellipse(x, y, rx, ry, r || 0, 0, Math.PI * 2); g.fill(); };
    if (m.kind === 'boot') {
      ell(0, -5, 5, 8); ell(0, 8, 4, 4);
      g.globalAlpha = a * 0.6; g.fillStyle = '#ecece8';
      for (let y = -11; y < 1; y += 3) g.fillRect(-4, y, 8, 1);
    } else if (m.kind === 'paw') {
      ell(0, 2, 3, 2.5);
      [[-3.5, -2], [-1.2, -3.5], [1.2, -3.5], [3.5, -2]].forEach(([x, y]) => ell(x, y, 1.1, 1.1));
    } else if (m.kind === 'butt') {
      ell(-9, 0, 9, 13, 0.15); ell(9, 0, 9, 13, -0.15);
    } else if (m.kind === 'knee') {
      ell(-7, -4, 5, 7); ell(7, -4, 5, 7);
      ell(-7, 14, 3.5, 3); ell(7, 14, 3.5, 3);
    } else if (m.kind === 'line') {
      g.fillRect(-2, -40, 4, 80);
    } else if (m.kind === 'glyph') {
      // burned, not pressed: a brown scorch with a darker line through the middle of each stroke
      g.globalAlpha = Math.min(1, a * 1.25);
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.font = '900 72px sans-serif';
      g.lineJoin = 'round';
      g.strokeStyle = 'rgba(90,62,38,0.45)';
      g.lineWidth = 11;
      g.strokeText(m.ch, 0, 2);
      g.fillStyle = '#2b1e14';
      g.fillText(m.ch, 0, 2);
    } else if (m.kind === 'ball') {
      g.globalAlpha = a * 0.55;
      ell(0, 0, 9, 9);
      g.globalAlpha = a;
      g.fillStyle = '#5a5c5f';
      ell(0, 0, 6, 6);
    } else if (m.kind === 'curl') {
      // a cat, asleep, as a hollow: round body, the head tucked in, the tail round the front
      g.globalAlpha = a * 0.7;
      ell(0, 0, 20, 16);
      ell(14, -10, 8, 7);
      g.globalAlpha = a;
      g.strokeStyle = '#3c3e41';
      g.lineWidth = 3;
      g.beginPath(); g.arc(0, 0, 21, 0.4, 2.6); g.stroke();
    } else if (m.kind === 'crater') {
      g.globalAlpha = a * 0.5;
      ell(0, 0, 22, 18, 0.4);
      g.globalAlpha = a;
      g.fillStyle = '#35373a';
      ell(0, 0, 12, 10, 0.4);
      g.strokeStyle = '#3a3c3f';
      g.lineWidth = 2;
      [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(([x, y]) => { g.beginPath(); g.moveTo(x * 10, y * 8); g.lineTo(x * 24, y * 20); g.stroke(); });
    } else if (m.kind === 'smear') {
      g.globalAlpha = a * 0.45;
      g.fillRect(-4, -14, 8, 28);
      g.globalAlpha = a * 0.7;
      g.fillRect(-1, -14, 2, 28);
    } else if (m.kind === 'rain') {
      // the same drops each redraw: the pattern comes from where the mark is, not from chance
      let seed = Math.floor((m.x + 20) * 997 + (m.z + 20) * 131);
      const r = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
      for (let k = 0; k < 24; k++) ell(r() * 68 - 34, r() * 68 - 34, 1 + r() * 1.5, 1 + r() * 1.5);
    } else {
      ell(0, 0, 2.6, 3.2);
    }
    g.restore();
  }

  // ------------------------------------------------------------------ the slab's shape, day by day
  // A pour one person can finish: a rectangle against the east side, where the pipe line comes in,
  // often with a corner or two cut out of it — an L, a step, a T. From 9 to 70 m².
  const ENTRY = { i: NX - 1, j: 7 };       // the square the pipe line ends at
  /**
   * A slab of any size one crew might get: a small garage floor, a medium extension, a big
   * warehouse bay. The size is picked first — spread from 9 m² to over 200 — then how long and
   * narrow it is, then its outline: a plain rectangle, an L, a T, a U, a step, a cross, with the
   * odd extra notch. It always has its east side on the square where the pipe line comes in.
   */
  function makeShape() {
    const band = weighted([[0.25, [9, 30]], [0.4, [30, 90]], [0.35, [90, 220]]]);
    const target = Math.round(Math.exp(rnd(Math.log(band[0]), Math.log(band[1]))));
    const kind = weighted([[0.2, 'rect'], [0.22, 'L'], [0.14, 'T'], [0.14, 'U'], [0.14, 'Z'], [0.08, 'cross'], [0.08, 'notch']]);
    for (let tries = 0; tries < 600; tries++) {
      // the outer box, before any cuts: a bit bigger than the target, as the cuts take some away
      const grow = kind === 'rect' ? 1 : kind === 'cross' ? 1.35 : 1.25;
      const aspect = rnd(1, 3.2);
      let W = Math.round(Math.sqrt(target * grow * aspect)), D = Math.round((target * grow) / Math.max(1, W));
      if (chance(0.5)) [W, D] = [D, W];
      W = clamp(W, 3, NX); D = clamp(D, 3, NZ);
      const on = new Array(NX * NZ).fill(false);
      const j0 = irnd(Math.max(0, ENTRY.j - D + 1), Math.min(ENTRY.j, NZ - D)), i0 = NX - W;
      for (let j = j0; j < j0 + D; j++) for (let i = i0; i < NX; i++) on[j * NX + i] = true;
      const cut = (ci, cj, w, d) => {
        // never where the pipe comes in
        if (ci + w >= NX && ENTRY.j >= cj && ENTRY.j < cj + d) return;
        for (let j = cj; j < cj + d; j++) for (let i = ci; i < ci + w; i++) if (i >= 0 && j >= 0 && i < NX && j < NZ) on[j * NX + i] = false;
      };
      const cw = () => irnd(1, Math.max(1, Math.floor(W * 0.55))), cd = () => irnd(1, Math.max(1, Math.floor(D * 0.55)));
      if (W >= 4 && D >= 4) {
        if (kind === 'L') { const c = pick(['nw', 'sw']); cut(i0, c === 'nw' ? j0 : j0 + D - cd(), cw(), D); const d = cd(); cut(i0, c === 'nw' ? j0 : j0 + D - d, cw(), d); }
        if (kind === 'T') { const w = cw(), d = cd(); cut(i0, j0, w, d); cut(i0, j0 + D - d, w, d); }
        if (kind === 'U') {
          // a bite out of the middle of the west, north or south side
          const side = pick(['w', 'n', 's']), w = irnd(1, Math.max(1, W - 2)), d = irnd(1, Math.max(1, D - 2));
          if (side === 'w') cut(i0, j0 + Math.floor((D - d) / 2), Math.min(w, W - 1), d);
          else cut(i0 + Math.floor((W - w) / 2), side === 'n' ? j0 : j0 + D - Math.min(d, D - 1), w, Math.min(d, D - 1));
        }
        if (kind === 'Z') { cut(i0, j0, cw(), cd()); const w = cw(), d = cd(); cut(NX - w, j0 + D - d, w, d); }
        if (kind === 'cross') { const w = Math.max(1, Math.floor(W / 3)), d = Math.max(1, Math.floor(D / 3)); cut(i0, j0, w, d); cut(i0, j0 + D - d, w, d); cut(NX - w, j0, w, d); cut(NX - w, j0 + D - d, w, d); }
        if (kind === 'notch' || chance(0.25)) { const w = irnd(1, 2), d = irnd(1, 2); cut(irnd(i0, NX - w), chance(0.5) ? j0 : j0 + D - d, w, d); }
      }
      const area = on.filter(Boolean).length;
      if (area < 9 || area < target * 0.65 || area > target * 1.4 || !connected(on)) continue;
      return { on, area, kind };
    }
    const on = new Array(NX * NZ).fill(false);
    for (let j = 4; j < 11; j++) for (let i = NX - 7; i < NX; i++) on[j * NX + i] = true;
    return { on, area: 49, kind: 'rect' };
  }
  /** Whether a set of squares is all one piece. */
  function connected(on) {
    const start = on.indexOf(true);
    if (start < 0) return false;
    const seen = new Set([start]), todo = [start];
    while (todo.length) {
      const k = todo.pop(), i = k % NX, j = Math.floor(k / NX);
      [[1, 0], [-1, 0], [0, 1], [0, -1]].forEach(([di, dj]) => {
        const ni = i + di, nj = j + dj, nk = nj * NX + ni;
        if (ni >= 0 && nj >= 0 && ni < NX && nj < NZ && on[nk] && !seen.has(nk)) { seen.add(nk); todo.push(nk); }
      });
    }
    return seen.size === on.filter(Boolean).length;
  }

  // everything that follows the shape: boards, base, mesh, pipes through it, the jobs round it
  const site = { on: new Array(NX * NZ).fill(false), area: 0, runs: [], edges: [], pens: [], ties: [], cuts: [], forms: [], weak: null, box: { x0: -6, x1: 6, z0: -4, z1: 4 }, mid: P(0, 0) };
  const siteGroup = new THREE.Group();
  scene.add(siteGroup);
  const baseMat = new THREE.MeshLambertMaterial({ map: noiseTex([140, 134, 122], 60, 1) });
  const rebarMat = new THREE.LineBasicMaterial({ color: 0x8e4b2c });
  const skirtMat = new THREE.MeshLambertMaterial({ color: 0x8f9193 });
  const PLY = 0xc9a26b, STAKE = 0x9b7a4a, RUST = 0x7d4127;
  let skirtGeo = null, skirtTop = [], slabVerts = [];
  function isOn(i, j) { return i >= 0 && j >= 0 && i < NX && j < NZ && site.on[j * NX + i]; }
  const gx = (i) => SLAB.x0 + i, gz = (j) => SLAB.z0 + j;
  /** The drawn height of the mesh: about a third of the way up the slab, on its chairs. */
  function rebarY() { return clamp(day.thick * 0.38, 30, 90) / 1000; }

  function buildSite() {
    siteGroup.traverse((o) => { if (o.geometry) o.geometry.dispose(); });
    siteGroup.clear();
    site.on = day.on;
    site.area = day.area;
    const cellsOn = [];
    for (let j = 0; j < NZ; j++) for (let i = 0; i < NX; i++) if (isOn(i, j)) cellsOn.push({ i, j });
    const xs = cellsOn.map((c) => c.i), zs = cellsOn.map((c) => c.j);
    site.box = { x0: gx(Math.min(...xs)), x1: gx(Math.max(...xs) + 1), z0: gz(Math.min(...zs)), z1: gz(Math.max(...zs) + 1) };
    site.mid = P(cellsOn.reduce((s, c) => s + gx(c.i) + 0.5, 0) / cellsOn.length, cellsOn.reduce((s, c) => s + gz(c.j) + 0.5, 0) / cellsOn.length);

    // the outline, as straight runs of board; `out` points away from the concrete
    const runs = [];
    for (let j = 0; j <= NZ; j++) {
      let cur = null;
      for (let i = 0; i <= NX; i++) {
        const a = isOn(i, j - 1), b = isOn(i, j);
        const side = i < NX && a !== b ? (b ? -1 : 1) : 0;
        if (cur && side === cur.s) { cur.len++; continue; }
        if (cur) runs.push(cur);
        cur = side ? { dir: 'x', s: side, i, j, len: 1 } : null;
      }
    }
    for (let i = 0; i <= NX; i++) {
      let cur = null;
      for (let j = 0; j <= NZ; j++) {
        const a = isOn(i - 1, j), b = isOn(i, j);
        const side = j < NZ && a !== b ? (b ? -1 : 1) : 0;
        if (cur && side === cur.s) { cur.len++; continue; }
        if (cur) runs.push(cur);
        cur = side ? { dir: 'z', s: side, i, j, len: 1 } : null;
      }
    }
    runs.forEach((r) => {
      if (r.dir === 'x') { r.x0 = gx(r.i); r.x1 = gx(r.i + r.len); r.z0 = r.z1 = gz(r.j); r.ox = 0; r.oz = r.s; }
      else { r.z0 = gz(r.j); r.z1 = gz(r.j + r.len); r.x0 = r.x1 = gx(r.i); r.ox = r.s; r.oz = 0; }
      r.mid = P((r.x0 + r.x1) / 2, (r.z0 + r.z1) / 2);
      // the squares of concrete along the inside of this board
      r.cells = [];
      for (let k = 0; k < r.len; k++) {
        if (r.dir === 'x') r.cells.push({ i: r.i + k, j: r.s < 0 ? r.j : r.j - 1 });
        else r.cells.push({ i: r.s < 0 ? r.i : r.i - 1, j: r.j + k });
      }
    });
    site.runs = runs;

    // the formwork: a board along every run, a little taller than the slab, and stakes behind it
    const H = day.thick / 1000 + 0.06;
    runs.forEach((r) => {
      const len = r.len + 0.06;
      const b = r.dir === 'x' ? box(len, H, 0.05, PLY, r.mid.x, H / 2, r.mid.z + r.oz * 0.028, siteGroup) : box(0.05, H, len, PLY, r.mid.x + r.ox * 0.028, H / 2, r.mid.z, siteGroup);
      b.castShadow = b.receiveShadow = true;
      for (let k = 0.4; k < r.len; k += 1.2) {
        const x = r.dir === 'x' ? r.x0 + k : r.x0 + r.ox * 0.09, z = r.dir === 'x' ? r.z0 + r.oz * 0.09 : r.z0 + k;
        box(0.05, H + 0.18, 0.05, STAKE, x, (H + 0.18) / 2 - 0.06, z, siteGroup).castShadow = true;
      }
    });

    // the base and the mesh on its chairs, only where the slab goes
    const bp = [], bu = [], bi = [];
    cellsOn.forEach((c, n) => {
      const x0 = gx(c.i), z0 = gz(c.j);
      bp.push(x0, 0.012, z0, x0 + 1, 0.012, z0, x0 + 1, 0.012, z0 + 1, x0, 0.012, z0 + 1);
      bu.push(x0 / 2, z0 / 2, (x0 + 1) / 2, z0 / 2, (x0 + 1) / 2, (z0 + 1) / 2, x0 / 2, (z0 + 1) / 2);
      bi.push(n * 4, n * 4 + 2, n * 4 + 1, n * 4, n * 4 + 3, n * 4 + 2);
    });
    const baseGeo = new THREE.BufferGeometry();
    baseGeo.setAttribute('position', new THREE.Float32BufferAttribute(bp, 3));
    baseGeo.setAttribute('uv', new THREE.Float32BufferAttribute(bu, 2));
    baseGeo.setIndex(bi);
    baseGeo.computeVertexNormals();
    const baseMesh = new THREE.Mesh(baseGeo, baseMat);
    baseMesh.receiveShadow = true;
    siteGroup.add(baseMesh);
    const ry = rebarY(), rp = [];
    cellsOn.forEach((c) => {
      const x0 = gx(c.i), z0 = gz(c.j);
      for (let k = 0.1; k < 1; k += 0.2) {
        rp.push(x0, ry, z0 + k, x0 + 1, ry, z0 + k);
        rp.push(x0 + k, ry + 0.008, z0, x0 + k, ry + 0.008, z0 + 1);
      }
    });
    const rebarGeo = new THREE.BufferGeometry();
    rebarGeo.setAttribute('position', new THREE.Float32BufferAttribute(rp, 3));
    site.rebar = new THREE.LineSegments(rebarGeo, rebarMat);
    siteGroup.add(site.rebar);

    // pipes through the slab: one or two, well inside it
    const inner = cellsOn.filter((c) => {
      for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) if (!isOn(c.i + di, c.j + dj)) return false;
      return Math.abs(c.i - ENTRY.i) + Math.abs(c.j - ENTRY.j) > 2;
    }).sort(() => Math.random() - 0.5);
    site.pens = [];
    for (const c of inner) {
      if (site.pens.length >= clamp(Math.round(site.area / 45), 1, 4)) break;
      if (site.pens.some((p) => Math.abs(p.i - c.i) + Math.abs(p.j - c.j) < 3)) continue;
      const p = { i: c.i, j: c.j, x: gx(c.i) + 0.5 + rnd(-0.2, 0.2), z: gz(c.j) + 0.5 + rnd(-0.2, 0.2) };
      p.mesh = cyl(0.08, 0.08, 0.8, 0xc4502a, p.x, 0.4, p.z, siteGroup);
      p.mesh.castShadow = true;
      site.pens.push(p);
    }

    // what has to be troweled by hand: every corner, the middle of each long edge, the collars
    const edges = [];
    for (let vj = 0; vj <= NZ; vj++) for (let vi = 0; vi <= NX; vi++) {
      const tl = isOn(vi - 1, vj - 1), tr = isOn(vi, vj - 1), bl = isOn(vi - 1, vj), br = isOn(vi, vj);
      const n = tl + tr + bl + br;
      if (n !== 1 && n !== 3) continue;
      // out: away from the one square of concrete, or towards the one square without
      const one = n === 1 ? (tl ? [-1, -1] : tr ? [1, -1] : bl ? [-1, 1] : [1, 1]) : (!tl ? [-1, -1] : !tr ? [1, -1] : !bl ? [-1, 1] : [1, 1]);
      // in: towards the concrete, where the trowel works, a hand's reach in from the corner
      const inX = n === 1 ? one[0] : -one[0], inZ = n === 1 ? one[1] : -one[1];
      edges.push({ kind: 'corner', label: n === 1 ? 'Corner' : 'Inside corner', x: gx(vi) + inX * 0.3, z: gz(vj) + inZ * 0.3, inX, inZ });
    }
    // straight edges: one spot on a run of four metres or more, two on a very long one
    runs.filter((r) => r.len >= (site.area > 90 ? 6 : 4)).forEach((r) => {
      const at = r.len >= 16 ? [1 / 3, 2 / 3] : [0.5];
      at.forEach((f) => edges.push({ kind: 'edge', label: 'Edge', x: lerp(r.x0, r.x1, f) - r.ox * 0.3, z: lerp(r.z0, r.z1, f) - r.oz * 0.3, inX: -r.ox, inZ: -r.oz, alongX: r.dir === 'x' ? 1 : 0, alongZ: r.dir === 'z' ? 1 : 0 }));
    });
    site.pens.forEach((p, k) => edges.push({ kind: 'collar', label: 'Pipe collar', x: p.x, z: p.z, pipe: k }));
    edges.forEach((e) => { e.done = false; });
    site.edges = edges;

    // the formwork to check: two long runs, one of them the weak one on a bad day
    const long = runs.slice().sort((a, b) => b.len - a.len);
    const notEntry = long.filter((r) => !(r.dir === 'z' && r.i === NX));
    site.weak = day.formworkWeak ? pick(notEntry.slice(0, 4)) : null;
    const other = long.find((r) => r !== site.weak && (!site.weak || hyp(r.mid.x, r.mid.z, site.weak.mid.x, site.weak.mid.z) > 3)) || long[0];
    site.forms = (site.weak ? [site.weak, other] : [long[0], long.find((r) => r !== long[0]) || long[0]]).map((r) => ({ run: r, x: r.mid.x + r.ox * 0.9, z: r.mid.z + r.oz * 0.9 }));

    // the mesh: a few loose bars to tie down, a bar or two sticking up to cut off
    const spots = cellsOn.filter((c) => !site.pens.some((p) => p.i === c.i && p.j === c.j)).sort(() => Math.random() - 0.5);
    site.ties = spots.slice(0, clamp(Math.round(site.area / 20), 2, 6)).map((c) => {
      const t = { x: gx(c.i) + 0.5, z: gz(c.j) + 0.5, done: false, i: c.i, j: c.j };
      t.bar = cyl(0.007, 0.007, 1.0, RUST, t.x, ry + 0.09, t.z, siteGroup, 5);
      t.bar.rotation.set(0.18, rnd(0, 3), Math.PI / 2);
      t.twist = mesh(new THREE.TorusGeometry(0.018, 0.004, 4, 8), 0xa9adb1, t.x, ry + 0.012, t.z, siteGroup);
      t.twist.rotation.x = Math.PI / 2;
      t.twist.visible = false;
      return t;
    });
    const edgeCells = spots.filter((c) => !isOn(c.i - 1, c.j) || !isOn(c.i + 1, c.j) || !isOn(c.i, c.j - 1) || !isOn(c.i, c.j + 1));
    site.cuts = edgeCells.filter((c) => !site.ties.some((t) => t.i === c.i && t.j === c.j)).slice(0, site.area > 100 ? 3 : site.area > 25 ? 2 : 1).map((c) => {
      const t = { x: gx(c.i) + 0.5 + rnd(-0.25, 0.25), z: gz(c.j) + 0.5 + rnd(-0.25, 0.25), done: false, i: c.i, j: c.j };
      t.bar = cyl(0.009, 0.009, 0.8, RUST, t.x, ry + 0.4, t.z, siteGroup, 5);
      t.bar.castShadow = true;
      return t;
    });

    site.hoseReach = Math.max(16, ...cellsOn.map((c) => hyp(gx(c.i) + 0.5, gz(c.j) + 0.5, PIPE_ROUTE[5].x, PIPE_ROUTE[5].z))) + 2;
    // the laser stands off the west side, where it can see the whole slab
    tripod.position.set(site.box.x0 - 2.4, 0, clamp(site.mid.z, -3, 3));
    POS.tripod = P(tripod.position.x, tripod.position.z);

    // the slab mesh only where there is slab, and a skirt round its edge down to the base
    const vx = NX * SEG + 1;
    const idx = [];
    cellsOn.forEach((c) => {
      for (let b = 0; b < SEG; b++) for (let a = 0; a < SEG; a++) {
        const ix = c.i * SEG + a, iy = c.j * SEG + b;
        const A = ix + vx * iy, B = ix + vx * (iy + 1), C = ix + 1 + vx * (iy + 1), D = ix + 1 + vx * iy;
        idx.push(A, B, D, B, C, D);
      }
    });
    slabGeo.setIndex(idx);
    slabVerts = [...new Set(idx)];
    const sp = [], sn = [], si = [];
    skirtTop = [];
    runs.forEach((r) => {
      const steps = r.len * SEG;
      const base0 = sp.length / 3;
      for (let k = 0; k <= steps; k++) {
        const ix = r.dir === 'x' ? r.i * SEG + k : r.i * SEG, iy = r.dir === 'x' ? r.j * SEG : r.j * SEG + k;
        const x = gx(0) + ix / SEG, z = gz(0) + iy / SEG;
        sp.push(x, 0.1, z, x, 0.012, z);
        sn.push(r.ox, 0, r.oz, r.ox, 0, r.oz);
        skirtTop.push(ix + vx * iy);
        if (k < steps) {
          const a = base0 + k * 2;
          si.push(a, a + 1, a + 2, a + 1, a + 3, a + 2);
        }
      }
    });
    if (skirtGeo) skirtGeo.dispose();
    skirtGeo = new THREE.BufferGeometry();
    skirtGeo.setAttribute('position', new THREE.Float32BufferAttribute(sp, 3));
    skirtGeo.setAttribute('normal', new THREE.Float32BufferAttribute(sn, 3));
    skirtGeo.setIndex(si);
    const skirt = new THREE.Mesh(skirtGeo, skirtMat);
    skirt.material.side = THREE.DoubleSide;
    siteGroup.add(skirt);
    cellsDirty = true;
  }

  /** The concrete's depth in mm at any point, from the squares of slab around it. */
  function fillAt(x, z) {
    const fx = clamp(x - SLAB.x0 - 0.5, 0, NX - 1), fz = clamp(z - SLAB.z0 - 0.5, 0, NZ - 1);
    const i0 = Math.floor(fx), j0 = Math.floor(fz);
    const i1 = Math.min(NX - 1, i0 + 1), j1 = Math.min(NZ - 1, j0 + 1);
    const tx = fx - i0, tz = fz - j0;
    let s = 0, w = 0;
    const add = (i, j, k) => { const c = gs.grid[j * NX + i]; if (c.on && k > 0) { s += c.fill * k; w += k; } };
    add(i0, j0, (1 - tx) * (1 - tz)); add(i1, j0, tx * (1 - tz)); add(i0, j1, (1 - tx) * tz); add(i1, j1, tx * tz);
    return w > 0 ? s / w : 0;
  }
  /**
   * How high concrete f mm deep is drawn: at its real depth, with the last two centimetres either
   * side of the laser height shown two and a half times over, so 5 mm high or low shows. (Drawn at
   * twice its depth all over, a 250 mm slab stood half a metre up, over the top of its formwork.)
   */
  function surfY(f) { return f < 3 ? -0.03 : Math.max(0.008, f / 1000 + (clamp(f - day.thick, -20, 20) * 1.5) / 1000) + 0.004; }
  function groundY(x, z) {
    if (!gs || !onSlab(x, z)) return 0;
    const f = fillAt(x, z);
    return f < 3 ? 0.012 : surfY(f);
  }

  // task markers
  const markers = [];
  const PROG_SEGS = 48;
  /** Fills a marker's ring to `frac` of the way round, starting from the side away from you. */
  function ringProgress(m, frac) {
    const on = frac > 0;
    m.progPivot.visible = on;
    m.ringMat.color.setHex(on ? 0x8a3a10 : 0xff6b1a);
    if (!on) { m.quarter = 0; return; }
    const dx = m.x - player.x, dz = m.z - player.z;
    m.progPivot.rotation.y = Math.atan2(-dz, dx);
    m.prog.geometry.setDrawRange(0, 6 * Math.max(1, Math.round(PROG_SEGS * frac)));
    // a tick at every quarter, so it can be heard filling as well as seen
    const q = Math.floor(frac * 4);
    if (q > m.quarter && q < 4) sfx('click');
    m.quarter = q;
  }
  function addMarker(id, p, label, hold, active, done, opts) {
    const g = new THREE.Group();
    const ringMat = new THREE.MeshBasicMaterial({ color: 0xff6b1a });
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.55, 0.05, 8, 36), ringMat);
    ring.rotation.x = Math.PI / 2;
    ring.position.y = 0.06;
    g.add(ring);
    // the hold countdown, drawn round the ring itself: a flat band laid over it that grows
    // clockwise from the far side as the button is held. A ring with one row of segments keeps
    // its triangles in angle order, so showing the first part of the list shows part of the arc.
    const progGeo = new THREE.RingGeometry(0.46, 0.65, PROG_SEGS, 1);
    progGeo.setDrawRange(0, 0);
    const prog = new THREE.Mesh(progGeo, new THREE.MeshBasicMaterial({ color: 0xfff1c2, side: THREE.DoubleSide }));
    prog.rotation.x = Math.PI / 2;    // flat, and the angle runs clockwise seen from above
    const progPivot = new THREE.Group();
    progPivot.position.y = 0.12;
    progPivot.add(prog);
    progPivot.visible = false;
    g.add(progPivot);
    const beamM = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 2.4, 8), new THREE.MeshBasicMaterial({ color: 0xff6b1a, transparent: true, opacity: 0.35 }));
    beamM.position.y = 1.2;
    g.add(beamM);
    const sprite = textSprite(label, { w: (opts && opts.w) || 2.6 });
    sprite.position.y = 2.7;
    g.add(sprite);
    g.position.set(p.x, (opts && opts.y) || 0, p.z);
    g.visible = false;
    scene.add(g);
    const m = { id, x: p.x, z: p.z, label, hold, active, done, group: g, ring, beam: beamM, ringMat, prog, progPivot, quarter: 0, sprite, tool: opts && opts.tool, byMachine: !!(opts && opts.byMachine) };
    markers.push(m);
    return m;
  }
  function removeMarker(m) {
    const k = markers.indexOf(m);
    if (k >= 0) markers.splice(k, 1);
    scene.remove(m.group);
  }

  // ------------------------------------------------------------------ the tools
  // Nothing is in your pocket. The tools come out of the van onto a tarp, the machines stand
  // beside it, and whatever you need you walk over and pick up; whatever you put down stays
  // where you put it.
  const TOOLS = {
    hose: { name: 'Hose', the: 'the hose' },
    float: { name: 'Float', the: 'the float' },
    handTrowel: { name: 'Hand trowel', the: 'the hand trowel' },
    hammer: { name: 'Hammer', the: 'the hammer' },
    pliers: { name: 'Pliers & wire', the: 'the pliers and wire' },
    cutter: { name: 'Rebar cutter', the: 'the rebar cutter' },
    trowelSmall: { name: 'Edge trowel', the: 'the edge trowel (the small one)', machine: true, R: 0.3 },
    trowelBig: { name: 'Power trowel', the: 'the power trowel', machine: true, R: 0.46 },
    rideOn: { name: 'Ride-on trowel', the: 'the ride-on trowel', machine: true, R: 0.46, ride: true },
  };
  const TOOL_IDS = Object.keys(TOOLS);
  // where each waits once the van is unloaded: hand tools on the tarp, the machines beside it
  const TOOL_HOME = {
    float: [-20.6, 5.0, 0], handTrowel: [-20.0, 5.6, 0.3], hammer: [-19.4, 5.1, -0.4], pliers: [-18.9, 5.7, 0.8], cutter: [-18.4, 5.1, 0.2],
    trowelSmall: [-18.1, 7.4, 0.3], trowelBig: [-20.2, 7.6, -0.2], rideOn: [-23.2, 3.2, 1.6],
  };
  const MATS_STEEL = new THREE.MeshPhongMaterial({ color: 0xdfe4e9, specular: 0xffffff, shininess: 80, side: THREE.DoubleSide });
  const WOOD = 0xb88a4a, GRIP = 0xd8392f, DARK = 0x2a2d31;

  // the hand tools, each built once for the hand and once to lie on the ground
  function mkHandTrowel(parent) {
    const g = new THREE.Group();
    const sh = new THREE.Shape();
    const L2 = 0.15, W2 = 0.055, rr = 0.03;
    sh.moveTo(-L2, -W2); sh.lineTo(L2 - rr, -W2); sh.quadraticCurveTo(L2, -W2, L2, -W2 + rr);
    sh.lineTo(L2, W2 - rr); sh.quadraticCurveTo(L2, W2, L2 - rr, W2); sh.lineTo(-L2, W2); sh.closePath();
    const blade = new THREE.Mesh(new THREE.ShapeGeometry(sh).rotateX(-Math.PI / 2), MATS_STEEL);
    g.add(blade);
    box(0.006, 0.05, 0.012, 0x6d7278, -0.04, 0.025, 0, g);
    box(0.006, 0.05, 0.012, 0x6d7278, 0.05, 0.025, 0, g);
    const h = cyl(0.016, 0.018, 0.15, WOOD, 0.005, 0.058, 0, g, 10);
    h.rotation.z = Math.PI / 2;
    parent.add(g);
    return g;
  }
  function mkHammer(parent) {
    const g = new THREE.Group();
    cyl(0.017, 0.02, 0.36, WOOD, 0, 0.18, 0, g, 8);
    box(0.17, 0.07, 0.07, 0x3a3d41, 0, 0.37, 0, g);
    box(0.02, 0.075, 0.075, 0x6d7278, 0.09, 0.37, 0, g);
    box(0.02, 0.075, 0.075, 0x6d7278, -0.09, 0.37, 0, g);
    parent.add(g);
    return g;
  }
  // tie wire comes on a bright galvanised coil; the pliers are the long-nosed tower pincers
  const WIRE = new THREE.MeshPhongMaterial({ color: 0xdfe6ec, specular: 0xffffff, shininess: 90 });
  function mkCoil(parent) {
    const g = new THREE.Group();
    for (let k = 0; k < 4; k++) {
      const t = new THREE.Mesh(new THREE.TorusGeometry(0.085 + k * 0.004, 0.009, 6, 24), WIRE);
      t.rotation.x = Math.PI / 2;
      t.position.y = k * 0.012;
      g.add(t);
    }
    // the loose end, sticking out the way it always does
    const end = new THREE.Mesh(new THREE.CylinderGeometry(0.004, 0.004, 0.14, 4), WIRE);
    end.rotation.z = Math.PI / 2 - 0.3;
    end.position.set(0.14, 0.03, 0);
    g.add(end);
    parent.add(g);
    return g;
  }
  function mkPliers(parent, coil) {
    const g = new THREE.Group();
    [-1, 1].forEach((sd) => {
      const arm = box(0.024, 0.018, 0.24, GRIP, sd * 0.024, 0, 0.12, g);
      arm.rotation.y = sd * 0.1;
    });
    box(0.05, 0.03, 0.05, 0x5a5f64, 0, 0, -0.01, g);
    const nose = new THREE.Mesh(new THREE.BoxGeometry(0.042, 0.024, 0.09), WIRE);
    nose.position.set(0, 0, -0.075);
    g.add(nose);
    if (coil) mkCoil(g).position.set(0.2, 0, 0.06);
    parent.add(g);
    return g;
  }
  function mkCutter(parent) {
    const g = new THREE.Group();
    // the head: two cheek plates and the pivot bolts, and a pair of short jaws
    box(0.075, 0.03, 0.14, 0x2a2d31, 0, 0, -0.02, g);
    [-0.05, 0.02].forEach((z) => { const b = cyl(0.012, 0.012, 0.045, 0x9ea3a8, 0, 0, z, g, 8); b.rotation.z = Math.PI / 2; });
    const jaws = [];
    [-1, 1].forEach((sd) => {
      const jaw = new THREE.Group();
      jaw.position.set(sd * 0.012, 0, -0.08);
      box(0.022, 0.024, 0.07, 0x8e9398, 0, 0, -0.035, jaw);
      jaw.rotation.y = -sd * 0.14;
      g.add(jaw);
      jaws.push(jaw);
    });
    // the handles, hinged at the head, spreading out to the grips
    const arms = [];
    [-1, 1].forEach((sd) => {
      const arm = new THREE.Group();
      arm.position.set(sd * 0.02, 0, 0.04);
      arm.rotation.y = sd * 0.1;
      const bar = cyl(0.011, 0.011, 0.6, 0x44484d, 0, 0, 0.3, arm, 6);
      bar.rotation.x = Math.PI / 2;
      const grip = cyl(0.019, 0.019, 0.2, GRIP, 0, 0, 0.5, arm, 8);
      grip.rotation.x = Math.PI / 2;
      g.add(arm);
      arms.push(arm);
    });
    g.userData = { arms, jaws };
    parent.add(g);
    return g;
  }

  // what is in your hands, drawn in front of the eye
  const hands = new THREE.Group();
  hands.position.set(0.32, -0.34, -0.62);
  camera.add(hands);
  const viewTools = {};
  let pliersBody = null;           // the pliers inside their group, which twists without the coil
  (function buildViewTools() {
    const hose = new THREE.Group();
    const h1 = cyl(0.035, 0.04, 0.55, 0x1d1f22, -0.02, -0.02, -0.3, hose);
    h1.rotation.x = Math.PI / 2 - 0.35;
    const nozzle = cyl(0.045, 0.045, 0.08, 0x44484d, -0.02, 0.07, -0.56, hose);
    nozzle.rotation.x = Math.PI / 2 - 0.35;
    viewTools.hose = hose;
    const carry = new THREE.Group();
    const cp = cyl(0.07, 0.07, 3, 0x6d7278, -0.3, 0.05, -0.2, carry, 10);
    cp.rotation.x = Math.PI / 2;
    cp.rotation.z = 0.15;
    viewTools.pipe = carry;
    // the laser, folded on its tripod, over the shoulder
    const laserC = new THREE.Group();
    [-0.03, 0, 0.03].forEach((o) => { const leg = cyl(0.012, 0.012, 1.3, 0xd8b23a, o, 0, -0.3, laserC, 6); leg.rotation.x = Math.PI / 2 - 0.5; });
    box(0.2, 0.18, 0.2, 0xd84a2a, 0, 0.3, -0.85, laserC);
    laserC.position.set(-0.2, 0, 0);
    viewTools.laser = laserC;
    const cup = new THREE.Group();
    cyl(0.05, 0.04, 0.12, 0xf2efe8, -0.05, 0, -0.2, cup);
    viewTools.cup = cup;
    // pivoting at the grip, so a swing turns about the wrist
    const hammer = new THREE.Group();
    mkHammer(hammer);
    hammer.position.set(-0.1, -0.12, -0.12);
    viewTools.hammer = hammer;
    // a finishing trowel: a steel blade with the handle along the top of it
    const ht = new THREE.Group();
    const htb = mkHandTrowel(ht);
    htb.rotation.y = Math.PI / 2 + 0.3;
    ht.position.set(-0.05, -0.16, -0.2);
    viewTools.handTrowel = ht;
    // pliers in the right hand, the coil of wire in the left
    const pl = new THREE.Group();
    const plb = mkPliers(pl, false);
    // the handles down in the fist, the nose up and forward, ready to twist
    plb.rotation.set(0.5, 0.4, -0.3);
    plb.scale.setScalar(1.35);
    pl.position.set(-0.2, 0.1, -0.1);
    const coilHand = new THREE.Group();
    mkCoil(coilHand).rotation.x = 1.1;
    coilHand.scale.setScalar(1.1);
    coilHand.position.set(-0.22, -0.13, -0.02);
    pl.add(coilHand);
    viewTools.pliers = pl;
    pliersBody = plb;
    const cu = new THREE.Group();
    const cub = mkCutter(cu);
    cub.rotation.set(-0.15, 0, 0);
    cub.scale.setScalar(0.85);
    cu.position.set(-0.3, 0.0, -0.5);
    cu.userData.parts = cub.userData;
    viewTools.cutter = cu;
    Object.values(viewTools).forEach((o) => { o.visible = false; hands.add(o); });
  })();

  // the same tools lying about the site
  const lying = {};
  (function buildLying() {
    const add = (id, fn) => { const g = new THREE.Group(); fn(g); g.visible = false; scene.add(g); lying[id] = g; };
    add('handTrowel', (g) => { mkHandTrowel(g).position.y = 0.004; });
    add('hammer', (g) => { const h = mkHammer(g); h.rotation.z = Math.PI / 2; h.position.set(0.18, 0.035, 0); });
    add('pliers', (g) => { const p = mkPliers(g, true); p.position.y = 0.012; p.scale.setScalar(1.4); });
    add('cutter', (g) => { mkCutter(g).position.y = 0.02; });
    add('float', (g) => {
      box(0.9, 0.02, 0.2, 0xaeb4b9, 0, 0.012, 0, g);
      const pole = cyl(0.016, 0.016, 2.4, 0xc79a52, 0, 0.03, 1.25, g, 8);
      pole.rotation.x = Math.PI / 2;
    });
    add('hose', (g) => {
      const coil = mesh(new THREE.TorusGeometry(0.3, 0.045, 6, 20, Math.PI * 1.6), 0x1d1f22, 0, 0.05, 0, g);
      coil.rotation.x = Math.PI / 2;
      cyl(0.05, 0.05, 0.1, 0x44484d, 0.3, 0.05, 0, g).rotation.z = Math.PI / 2;
    });
  })();
  // the hand trowel at work, drawn on the concrete where the corner or edge is, with an arm to it
  const workTrowel = new THREE.Group();
  mkHandTrowel(workTrowel);
  workTrowel.visible = false;
  scene.add(workTrowel);
  const workArm = cyl(0.045, 0.04, 1, 0x3b5b8c, 0, 0, 0, scene, 8);
  workArm.visible = false;
  // a blue tarp for the tools to wait on
  const tarp = box(2.8, 0.01, 1.3, 0x2c6ad6, POS.tarp.x, 0.006, POS.tarp.z);
  tarp.visible = false;

  // the machines: two walk-behind power trowels — a small one that gets into the edges and a 90 cm
  // one for the field — and, for big slabs, a ride-on with two rotors. Pans or blades go on each.
  function mkRotor(R, parent, x) {
    const rotor = new THREE.Group();
    rotor.position.set(x || 0, 0.03, 0);
    parent.add(rotor);
    const pan = new THREE.Group();
    cyl(R, R, 0.014, 0x8e9398, 0, 0, 0, pan, 36);
    const ring = mesh(new THREE.TorusGeometry(R * 0.7, 0.01, 6, 30), 0x6d7277, 0, 0.01, 0, pan);
    ring.rotation.x = Math.PI / 2;
    rotor.add(pan);
    const blades = new THREE.Group();
    [0, 1, 2, 3].forEach((k) => {
      const arm = new THREE.Group();
      arm.rotation.y = (k * Math.PI) / 2;
      box(R * 0.78, 0.008, R * 0.24, 0xb9bec3, R * 0.52, 0.006, 0, arm);
      blades.add(arm);
    });
    rotor.add(blades);
    const guard = mesh(new THREE.TorusGeometry(R + 0.03, 0.035 * (R / 0.46 + 0.3), 8, 40), 0x2f3438, x || 0, 0.1, 0, parent);
    guard.rotation.x = Math.PI / 2;
    return { rotor, pan, blades };
  }
  function mkTrowel(R) {
    const g = new THREE.Group();
    const k = R / 0.46;
    const r = mkRotor(R, g);
    [0, 1, 2, 3].forEach((n) => {
      const a = (n * Math.PI) / 2 + Math.PI / 4;
      const spoke = box(0.035, 0.035, R, 0x2f3438, Math.sin(a) * R * 0.5, 0.17, Math.cos(a) * R * 0.5, g);
      spoke.rotation.y = a;
    });
    cyl(0.08 * k, 0.1 * k, 0.18, DARK, 0, 0.14, 0, g);
    box(0.34 * k, 0.24 * k, 0.28 * k, 0xff6b1a, 0, 0.2 + 0.14 * k, 0, g);
    box(0.27 * k, 0.1 * k, 0.22 * k, 0x1d1f22, 0, 0.2 + 0.31 * k, -0.02, g);
    cyl(0.04, 0.04, 0.05, 0x999999, 0.18 * k, 0.2 + 0.16 * k, 0, g).rotation.z = Math.PI / 2;
    // the handle runs back to waist height, where the operator's hands are
    const grip = 1.75;
    const handle = cyl(0.02, 0.02, Math.hypot(grip - 0.1, 0.5), DARK, 0, 0.7, (grip + 0.1) / 2, g);
    handle.rotation.x = Math.atan2(grip - 0.1, 0.5);
    box(0.56, 0.03, 0.03, DARK, 0, 0.95, grip, g);
    box(0.08, 0.04, 0.04, 0x111111, 0.24, 0.95, grip, g);
    box(0.08, 0.04, 0.04, 0x111111, -0.24, 0.95, grip, g);
    scene.add(g);
    return { group: g, rotors: [r], R, grip, twin: false };
  }
  function mkRideOn() {
    const g = new THREE.Group();
    const R = 0.46;
    const left = mkRotor(R, g, -0.52), right = mkRotor(R, g, 0.52);
    box(2.1, 0.1, 1.1, 0x3a3d41, 0, 0.26, 0, g);
    cyl(0.1, 0.12, 0.2, DARK, -0.52, 0.16, 0, g);
    cyl(0.1, 0.12, 0.2, DARK, 0.52, 0.16, 0, g);
    box(0.8, 0.45, 0.5, 0xff6b1a, 0, 0.54, 0.25, g);
    box(0.5, 0.08, 0.45, 0x1d1f22, 0, 0.84, 0.3, g);
    box(0.5, 0.4, 0.06, 0x1d1f22, 0, 1.05, 0.52, g);
    cyl(0.12, 0.12, 0.4, 0xe9e7e2, 0.62, 0.45, 0.3, g).rotation.x = Math.PI / 2;
    [-0.42, 0.42].forEach((x) => {
      cyl(0.013, 0.013, 0.55, DARK, x, 0.6, -0.12, g, 6);
      mesh(new THREE.SphereGeometry(0.03, 8, 6), 0x111111, x, 0.89, -0.12, g);
    });
    box(0.9, 0.06, 0.08, 0x2a2d31, 0, 0.4, -0.58, g);
    [-0.35, 0.35].forEach((x) => box(0.12, 0.06, 0.03, 0xfff3d6, x, 0.4, -0.63, g));
    scene.add(g);
    return { group: g, rotors: [left, right], R, grip: 0, twin: true };
  }
  const machines = { trowelSmall: mkTrowel(0.3), trowelBig: mkTrowel(0.46), rideOn: mkRideOn() };
  Object.values(machines).forEach((m) => { m.group.visible = false; m.spin = 0; });

  // a magnesium float on a pole, for levelling the pour and smoothing out prints
  const floatTool = new THREE.Group();
  box(0.9, 0.02, 0.2, 0xaeb4b9, 0, 0.012, 0, floatTool);
  floatTool.visible = false;
  scene.add(floatTool);
  const floatPole = cyl(0.016, 0.016, 1, 0xc79a52, 0, 0, 0, scene, 8);
  floatPole.visible = false;

  // everything solid casts a shadow and takes one; the sky, the markers and the glow do not, and
  // neither does what is in your hands — it would throw a shadow the size of a door
  scene.traverse((o) => {
    if (!o.isMesh || !(o.material instanceof THREE.MeshLambertMaterial)) return;
    o.castShadow = true;
    o.receiveShadow = true;
  });
  hands.traverse((o) => { o.castShadow = false; });
  ground.castShadow = false;
  grass.castShadow = false;

  const stream = cyl(0.06, 0.06, 1, 0x7d7f80, 0, 0, 0, scene, 8);
  stream.visible = false;
  const endHose = cyl(0.045, 0.045, 1, 0x1d1f22, 0, 0, 0, scene, 8);
  endHose.visible = false;
  function stretch(m, a, b) {
    const dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
    const len = Math.hypot(dx, dy, dz) || 0.001;
    m.position.set((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2);
    m.scale.set(1, len, 1);
    m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), new THREE.Vector3(dx / len, dy / len, dz / len));
  }

  // ------------------------------------------------------------------ sound
  // Every sound is made here, from noise and a few oscillators: no recordings to ship, and the
  // pump, the machine and the wind can follow what is happening rather than loop a clip.
  // The context can only start from a tap, so it is made on the first one.
  let ac = null, master = null, noiseBuf = null;
  let soundOn = store('pourday.sound') !== 'off';
  const loops = {};
  function audioStart() {
    if (ac) { if (ac.state === 'suspended' && !document.hidden) ac.resume().catch(() => {}); return; }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    try { ac = new AC(); } catch (e) { ac = null; return; }
    master = ac.createGain();
    master.gain.value = soundOn ? 0.9 : 0;
    const comp = ac.createDynamicsCompressor();
    comp.threshold.value = -14;
    comp.ratio.value = 4;
    master.connect(comp);
    comp.connect(ac.destination);
    noiseBuf = ac.createBuffer(1, ac.sampleRate * 2, ac.sampleRate);
    const d = noiseBuf.getChannelData(0);
    for (let k = 0; k < d.length; k++) d[k] = Math.random() * 2 - 1;
    buildLoops();
  }
  function setSound(on) {
    soundOn = on;
    store('pourday.sound', on ? 'on' : 'off');
    if (master) master.gain.setTargetAtTime(on ? 0.9 : 0, ac.currentTime, 0.05);
  }
  /** Asleep with the app in the background; awake again when it comes back. */
  function audioSleep(asleep) {
    if (!ac) return;
    if (asleep) ac.suspend().catch(() => {});
    else ac.resume().catch(() => {});
  }
  document.addEventListener('visibilitychange', () => audioSleep(document.hidden));
  window.pdSleep = audioSleep;

  function noiseSrc(loop) {
    const s = ac.createBufferSource();
    s.buffer = noiseBuf;
    s.loop = !!loop;
    if (loop) s.loopStart = Math.random();
    return s;
  }
  function filt(type, freq, q) {
    const f = ac.createBiquadFilter();
    f.type = type;
    f.frequency.value = freq;
    if (q) f.Q.value = q;
    return f;
  }
  function chain(...nodes) { for (let k = 0; k < nodes.length - 1; k++) nodes[k].connect(nodes[k + 1]); return nodes[nodes.length - 1]; }
  /** Out through a panner, placed left or right of you by where the sound is. */
  function out(x, z) {
    const p = ac.createStereoPanner ? ac.createStereoPanner() : ac.createGain();
    if (p.pan && x !== undefined) p.pan.value = panFor(x, z);
    p.connect(master);
    return p;
  }
  function panFor(x, z) {
    const dx = x - player.x, dz = z - player.z;
    const len = Math.hypot(dx, dz);
    if (len < 0.5) return 0;
    const rx = Math.cos(player.yaw), rz = -Math.sin(player.yaw);
    return clamp((dx * rx + dz * rz) / len, -1, 1) * 0.8;
  }
  function near(x, z, range) { return clamp(1 - hyp(x, z, player.x, player.z) / range, 0, 1); }
  function env(g, t, a, peak, d) {
    g.gain.setValueAtTime(0.0001, t);
    g.gain.linearRampToValueAtTime(peak, t + a);
    g.gain.exponentialRampToValueAtTime(0.0001, t + a + d);
  }
  function burst(t, type, freq, q, peak, a, d, dest, rate) {
    const s = noiseSrc();
    if (rate) s.playbackRate.value = rate;
    const g = ac.createGain();
    env(g, t, a, peak, d);
    chain(s, filt(type, freq, q), g, dest);
    s.start(t, Math.random());
    s.stop(t + a + d + 0.05);
    return s;
  }
  function tone(t, type, f0, f1, peak, a, d, dest) {
    const o = ac.createOscillator();
    o.type = type;
    o.frequency.setValueAtTime(f0, t);
    if (f1 !== f0) o.frequency.exponentialRampToValueAtTime(f1, t + a + d);
    const g = ac.createGain();
    env(g, t, a, peak, d);
    chain(o, g, dest);
    o.start(t);
    o.stop(t + a + d + 0.05);
    return o;
  }
  /** A gibberish voice: a buzz through two formants that wander like vowels. */
  function voice(t, pitch, syllables, dest) {
    const o = ac.createOscillator();
    o.type = 'sawtooth';
    o.frequency.value = pitch;
    const f1 = filt('bandpass', 700, 6), f2 = filt('bandpass', 1200, 8);
    const g = ac.createGain();
    g.gain.value = 0.0001;
    o.connect(f1); o.connect(f2); f1.connect(g); f2.connect(g); g.connect(dest);
    let at = t;
    for (let k = 0; k < syllables; k++) {
      const len = rnd(0.09, 0.18);
      f1.frequency.setValueAtTime(rnd(300, 850), at);
      f2.frequency.setValueAtTime(rnd(900, 2300), at);
      o.frequency.setValueAtTime(pitch * rnd(0.85, 1.25), at);
      g.gain.setValueAtTime(0.0001, at);
      g.gain.linearRampToValueAtTime(0.35, at + 0.02);
      g.gain.linearRampToValueAtTime(0.0001, at + len);
      at += len + rnd(0.02, 0.07);
    }
    o.start(t);
    o.stop(at + 0.05);
  }

  /** One-off sounds, by name, at a place in the world or in your own head. */
  function sfx(name, x, z) {
    if (!ac || !soundOn || ac.state !== 'running') return;
    const t = ac.currentTime + 0.01;
    const far = x === undefined ? 1 : Math.max(0.08, near(x, z, 45));
    const o = out(x, z);
    const dest = ac.createGain();
    dest.gain.value = far;
    dest.connect(o);
    switch (name) {
      case 'gravel': burst(t, 'bandpass', rnd(1400, 2200), 0.8, 0.22, 0.005, 0.09, dest); burst(t + 0.02, 'highpass', 3000, 0.5, 0.08, 0.004, 0.05, dest); break;
      case 'wet': burst(t, 'lowpass', 520, 1, 0.3, 0.02, 0.2, dest); tone(t + 0.04, 'sine', 320, 110, 0.12, 0.02, 0.16, dest); break;
      case 'soft': burst(t, 'lowpass', 900, 0.7, 0.14, 0.01, 0.1, dest); break;
      case 'hard': burst(t, 'bandpass', 2600, 1.4, 0.16, 0.002, 0.04, dest); break;
      case 'clank':
        tone(t, 'triangle', 520, 505, 0.22, 0.002, 0.45, dest); tone(t, 'triangle', 1370, 1330, 0.12, 0.002, 0.3, dest);
        burst(t, 'highpass', 2500, 0.6, 0.2, 0.001, 0.05, dest);
        break;
      case 'hammer':
        tone(t, 'sine', 170, 70, 0.4, 0.002, 0.14, dest);
        burst(t, 'bandpass', 1600, 1.2, 0.35, 0.001, 0.07, dest);
        tone(t, 'triangle', 1900, 1850, 0.05, 0.001, 0.12, dest);
        break;
      case 'beep': tone(t, 'sine', 2900, 2900, 0.12, 0.005, 0.07, dest); tone(t + 0.13, 'sine', 2900, 2900, 0.12, 0.005, 0.07, dest); break;
      case 'honk':
        [0, 0.45].forEach((d) => { tone(t + d, 'square', 390, 385, 0.1, 0.02, 0.32, dest); tone(t + d, 'square', 494, 490, 0.08, 0.02, 0.32, dest); });
        break;
      case 'reverse': for (let k = 0; k < 4; k++) tone(t + k * 0.5, 'square', 1050, 1050, 0.05, 0.005, 0.26, dest); break;
      case 'brake': burst(t, 'highpass', 3200, 0.4, 0.35, 0.01, 0.7, dest); break;
      case 'bark': [0, 0.22].forEach((d) => { tone(t + d, 'sawtooth', 560, 280, 0.2, 0.01, 0.1, dest); burst(t + d, 'bandpass', 900, 2, 0.25, 0.005, 0.1, dest); }); break;
      case 'voice': voice(t, rnd(110, 210), irnd(4, 8), dest); break;
      case 'thud': tone(t, 'sine', 95, 38, 0.6, 0.005, 0.35, dest); burst(t, 'lowpass', 400, 0.7, 0.4, 0.005, 0.25, dest); break;
      case 'splash': burst(t, 'lowpass', 1300, 0.7, 0.4, 0.01, 0.55, dest); burst(t + 0.05, 'bandpass', 600, 1, 0.3, 0.02, 0.4, dest); break;
      case 'slurp': {
        const s = noiseSrc(); const f = filt('bandpass', 700, 5); const g = ac.createGain();
        f.frequency.setValueAtTime(600, t); f.frequency.linearRampToValueAtTime(1700, t + 0.45);
        env(g, t, 0.05, 0.3, 0.45);
        chain(s, f, g, dest); s.start(t); s.stop(t + 0.6);
        break;
      }
      case 'alarm':
        for (let k = 0; k < 4; k++) for (let n = 0; n < 3; n++) tone(t + k * 0.6 + n * 0.12, 'square', 1250, 1250, 0.05, 0.004, 0.07, dest);
        break;
      case 'click': tone(t, 'sine', 1700, 1200, 0.05, 0.001, 0.03, dest); break;
      case 'chime': tone(t, 'sine', 880, 880, 0.12, 0.005, 0.5, dest); tone(t + 0.09, 'sine', 1318, 1318, 0.1, 0.005, 0.6, dest); break;
      case 'buzz': tone(t, 'square', 196, 190, 0.06, 0.005, 0.16, dest); tone(t + 0.19, 'square', 165, 160, 0.06, 0.005, 0.2, dest); break;
      case 'pop': tone(t, 'sine', 600, 900, 0.08, 0.005, 0.08, dest); break;
      case 'ring':
        for (let k = 0; k < 2; k++) for (let n = 0; n < 12; n++) tone(t + k * 0.9 + n * 0.035, 'sine', n % 2 ? 1400 : 1750, n % 2 ? 1400 : 1750, 0.07, 0.003, 0.03, dest);
        break;
      case 'engine': tone(t, 'sawtooth', 48, 70, 0.18, 0.3, 1.8, dest); burst(t, 'lowpass', 300, 0.5, 0.25, 0.3, 1.8, dest); break;
      case 'shout': { const g2 = ac.createGain(); g2.gain.value = 1.8; g2.connect(dest); voice(t, rnd(95, 115), 3, g2); break; }
      case 'pickup': burst(t, 'bandpass', 2600, 2, 0.14, 0.003, 0.08, dest); tone(t + 0.02, 'triangle', 900, 700, 0.06, 0.003, 0.08, dest); break;
      case 'putdown': tone(t, 'sine', 160, 80, 0.25, 0.003, 0.12, dest); burst(t, 'lowpass', 700, 0.7, 0.18, 0.003, 0.1, dest); break;
      case 'thunk': tone(t, 'sine', 95, 45, 0.4, 0.004, 0.22, dest); burst(t, 'lowpass', 500, 0.7, 0.3, 0.004, 0.18, dest); tone(t, 'triangle', 420, 400, 0.05, 0.002, 0.25, dest); break;
      case 'pullstart':
        [0, 0.45].forEach((d) => { const s2 = noiseSrc(); const f = filt('bandpass', 600, 3); const g2 = ac.createGain(); f.frequency.setValueAtTime(500, t + d); f.frequency.exponentialRampToValueAtTime(2500, t + d + 0.28); env(g2, t + d, 0.02, 0.25, 0.3); chain(s2, f, g2, dest); s2.start(t + d); s2.stop(t + d + 0.4); });
        tone(t + 0.8, 'sawtooth', 35, 75, 0.2, 0.08, 0.9, dest);
        burst(t + 0.8, 'lowpass', 400, 0.8, 0.25, 0.08, 0.8, dest);
        break;
      case 'twist': for (let k = 0; k < 3; k++) tone(t + k * 0.07, 'square', 2600 + k * 400, 3800 + k * 400, 0.03, 0.003, 0.05, dest); break;
      case 'snip': burst(t, 'highpass', 4000, 0.7, 0.4, 0.001, 0.03, dest); tone(t + 0.01, 'triangle', 2500, 2380, 0.12, 0.001, 0.35, dest); tone(t + 0.01, 'sine', 3700, 3650, 0.05, 0.001, 0.25, dest); break;
      case 'door': burst(t, 'lowpass', 900, 0.6, 0.25, 0.05, 0.25, dest); tone(t + 0.3, 'sine', 110, 55, 0.4, 0.003, 0.2, dest); burst(t + 0.3, 'lowpass', 600, 0.7, 0.3, 0.003, 0.15, dest); break;
      case 'phoneYell': {
        const g2 = ac.createGain(); g2.gain.value = 2.4;
        const bp = filt('bandpass', 1400, 0.8);
        bp.connect(g2); g2.connect(dest);
        voice(t, rnd(150, 185), 18, bp);
        break;
      }
      case 'rx': tone(t, 'square', 2900, 2900, 0.035, 0.002, 0.045, dest); break;
      case 'ufo': {
        // a theremin with opinions
        const o = ac.createOscillator(), g2 = ac.createGain(), v = ac.createOscillator(), vd = ac.createGain();
        o.type = 'sine'; v.frequency.value = 6; vd.gain.value = 40;
        o.frequency.setValueAtTime(300, t); o.frequency.exponentialRampToValueAtTime(900, t + 1.6); o.frequency.exponentialRampToValueAtTime(420, t + 3.4);
        v.connect(vd); vd.connect(o.frequency);
        env(g2, t, 0.3, 0.16, 3.4);
        o.connect(g2); g2.connect(dest);
        o.start(t); v.start(t); o.stop(t + 3.8); v.stop(t + 3.8);
        tone(t, 'sawtooth', 55, 52, 0.08, 0.5, 3.2, dest);
        break;
      }
      case 'burn': burst(t, 'highpass', 3500, 0.5, 0.22, 0.02, 0.5, dest); tone(t, 'sine', 180, 120, 0.1, 0.01, 0.3, dest); break;
      case 'bounce': tone(t, 'sine', 140, 70, 0.35, 0.002, 0.12, dest); burst(t, 'lowpass', 800, 0.8, 0.2, 0.002, 0.06, dest); break;
      case 'drone': for (let k = 0; k < 4; k++) tone(t + k * 0.9, 'sawtooth', 220 + k * 6, 230 + k * 6, 0.05, 0.1, 0.9, dest); break;
      case 'meow': tone(t, 'sawtooth', 520, 820, 0.07, 0.03, 0.18, dest); tone(t + 0.18, 'sawtooth', 820, 440, 0.07, 0.01, 0.35, dest); break;
      case 'bird': {
        const f = rnd(2800, 4200);
        for (let k = 0, n = irnd(2, 5); k < n; k++) tone(t + k * 0.13, 'sine', f * rnd(0.9, 1.1), f * rnd(1.15, 1.4), 0.04, 0.005, 0.07, dest);
        break;
      }
      default: break;
    }
  }

  /** Sounds that run while something runs; each is a gain the frame loop turns up and down. */
  function buildLoops() {
    const make = (name, build) => {
      const g = ac.createGain();
      g.gain.value = 0;
      const p = ac.createStereoPanner ? ac.createStereoPanner() : ac.createGain();
      g.connect(p);
      p.connect(master);
      build(g);
      loops[name] = { g, p };
    };
    const lfo = (freq, depth, param) => {
      const o = ac.createOscillator(); o.frequency.value = freq;
      const d = ac.createGain(); d.gain.value = depth;
      o.connect(d); d.connect(param); o.start();
      return o;
    };
    // the line pump: a diesel and the thump of the pistons
    make('pump', (g) => {
      const o = ac.createOscillator(); o.type = 'sawtooth'; o.frequency.value = 46;
      const lp = filt('lowpass', 260, 1);
      const beat = ac.createGain(); beat.gain.value = 0.55;
      lfo(1.4, 0.45, beat.gain);
      chain(o, lp, beat, g); o.start();
      const n = noiseSrc(true); chain(n, filt('bandpass', 140, 0.8), beat); n.start();
    });
    // the mixer drum turning
    make('mixer', (g) => {
      const n = noiseSrc(true); const lp = filt('lowpass', 380, 0.7);
      const slosh = ac.createGain(); slosh.gain.value = 0.6;
      lfo(0.45, 0.4, slosh.gain);
      chain(n, lp, slosh, g); n.start();
      const o = ac.createOscillator(); o.type = 'triangle'; o.frequency.value = 58;
      const og = ac.createGain(); og.gain.value = 0.25; chain(o, og, g); o.start();
    });
    // the power trowel: a small petrol engine and the disc hissing over the paste
    make('trowel', (g) => {
      const o = ac.createOscillator(); o.type = 'sawtooth'; o.frequency.value = 62;
      const lp = filt('lowpass', 900, 1.5);
      const og = ac.createGain(); og.gain.value = 0.5;
      lfo(9, 6, o.frequency);
      chain(o, lp, og, g); o.start();
      loops._trowelOsc = o;
      const n = noiseSrc(true); const bp = filt('bandpass', 2200, 0.9);
      const ng = ac.createGain(); ng.gain.value = 0;
      chain(n, bp, ng, g); n.start();
      loops._trowelHiss = ng;
    });
    // concrete out of the hose, and water out of the other hose
    make('pour', (g) => {
      const n = noiseSrc(true); const lp = filt('lowpass', 480, 1.2);
      const gl = ac.createGain(); gl.gain.value = 0.7;
      lfo(7.5, 0.35, gl.gain);
      chain(n, lp, gl, g); n.start();
    });
    make('water', (g) => { const n = noiseSrc(true); chain(n, filt('bandpass', 2600, 0.5), g); n.start(); });
    // a float or a hand trowel scraping
    make('scrape', (g) => {
      const n = noiseSrc(true); const bp = filt('bandpass', 1100, 1.4);
      const sg = ac.createGain(); sg.gain.value = 0.6;
      lfo(2.2, 0.4, sg.gain);
      chain(n, bp, sg, g); n.start();
    });
    // weather and night
    make('wind', (g) => {
      const n = noiseSrc(true); const lp = filt('lowpass', 420, 0.6);
      lfo(0.09, 180, lp.frequency);
      chain(n, lp, g); n.start();
    });
    make('rain', (g) => { const n = noiseSrc(true); chain(n, filt('highpass', 2400, 0.4), g); n.start(); });
    make('crickets', (g) => {
      const o = ac.createOscillator(); o.frequency.value = 4400;
      const am = ac.createGain(); am.gain.value = 0;
      lfo(28, 0.5, am.gain);
      const gate = ac.createGain(); gate.gain.value = 0.5;
      lfo(0.7, 0.5, gate.gain);
      chain(o, am, gate, g); o.start();
    });
    make('traffic', (g) => {
      const n = noiseSrc(true); const lp = filt('lowpass', 260, 0.6);
      lfo(0.05, 120, lp.frequency);
      chain(n, lp, g); n.start();
    });
    make('hydraulic', (g) => {
      const o = ac.createOscillator(); o.type = 'sawtooth'; o.frequency.value = 150;
      lfo(3, 12, o.frequency);
      chain(o, filt('lowpass', 650, 2), g); o.start();
      const n = noiseSrc(true); chain(n, filt('bandpass', 1900, 1.5), g); n.start();
    });
    make('rxTone', (g) => { const o = ac.createOscillator(); o.type = 'square'; o.frequency.value = 2900; chain(o, filt('lowpass', 4000, 0.7), g); o.start(); });
    make('engine', (g) => {
      const o = ac.createOscillator(); o.type = 'sawtooth'; o.frequency.value = 38;
      chain(o, filt('lowpass', 200, 1), g); o.start();
      const n = noiseSrc(true); chain(n, filt('lowpass', 160, 0.5), g); n.start();
    });
  }
  function loopTo(name, level, x, z) {
    const l = loops[name];
    if (!l) return;
    l.g.gain.setTargetAtTime(level, ac.currentTime, 0.12);
    if (l.p.pan) l.p.pan.setTargetAtTime(x === undefined ? 0 : panFor(x, z), ac.currentTime, 0.1);
  }
  let birdAt = 0, rainUntil = 0, rxNext = 0;

  // light background music, made up as it goes: a pad over four chords, a soft bass, the odd
  // plucked note with an echo, a whisper of hi-hat. On unless switched off.
  let musicOn = store('pourday.music') !== 'off';
  let musicGain = null, musicEcho = null, musicNext = 0, musicStep = 0, musicWant = -1;
  const CHORDS = [[57, 60, 64, 67, 71], [53, 57, 60, 64, 67], [48, 52, 55, 59, 62], [55, 59, 62, 64, 67]];
  const MELODY = [57, 60, 62, 64, 67, 69, 72, 74, 76];
  const mtof = (n) => 440 * Math.pow(2, (n - 69) / 12);
  function musicBus() {
    if (musicGain || !ac) return;
    musicGain = ac.createGain();
    musicGain.gain.value = musicOn ? 0.55 : 0;
    musicGain.connect(master);
    musicEcho = ac.createDelay(1);
    musicEcho.delayTime.value = 0.39;
    const fb = ac.createGain(); fb.gain.value = 0.32;
    const wet = ac.createGain(); wet.gain.value = 0.35;
    musicEcho.connect(fb); fb.connect(musicEcho); musicEcho.connect(wet); wet.connect(musicGain);
  }
  function note(t, type, n, peak, a, d, dest, cutoff) {
    const o = ac.createOscillator(); o.type = type; o.frequency.value = mtof(n);
    const g = ac.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.linearRampToValueAtTime(peak, t + a);
    g.gain.exponentialRampToValueAtTime(0.0001, t + a + d);
    chain(o, filt('lowpass', cutoff || 1400, 0.5), g, dest);
    o.start(t); o.stop(t + a + d + 0.1);
  }
  function musicTick() {
    if (!ac || !musicOn || ac.state !== 'running') return;
    musicBus();
    const e = 60 / 76 / 2;
    if (musicNext < ac.currentTime) musicNext = ac.currentTime + 0.05;
    while (musicNext < ac.currentTime + 0.3) {
      const t = musicNext, step = musicStep % 16, chord = CHORDS[Math.floor(musicStep / 16) % 4];
      if (step === 0) chord.forEach((n) => { note(t, 'triangle', n, 0.02, 1.2, e * 16, musicGain, 900); note(t, 'sine', n + 12, 0.008, 1.5, e * 14, musicGain, 1800); });
      if (step === 0 || step === 8) note(t, 'sine', chord[0] - 12, 0.09, 0.02, e * 3, musicGain, 500);
      if (step === 5 || step === 13) note(t, 'sine', chord[0] - 5, 0.05, 0.02, e * 1.5, musicGain, 500);
      if (step % 2 === 0 && chance(0.24)) { const g = ac.createGain(); g.gain.value = 1; g.connect(musicGain); g.connect(musicEcho); note(t, 'triangle', pick(MELODY) + (chance(0.25) ? 12 : 0), 0.035, 0.01, e * pick([1.5, 2.5, 3.5]), g, 2400); }
      if (step % 2 === 1) burst(t, 'highpass', 7500, 0.5, 0.012, 0.002, 0.04, musicGain);
      musicNext += e;
      musicStep++;
    }
  }
  function setMusic(on) {
    musicOn = on;
    store('pourday.music', on ? 'on' : 'off');
    if (ac) { musicBus(); musicWant = on ? 0.55 : 0; musicGain.gain.setTargetAtTime(musicWant, ac.currentTime, 0.3); }
  }
  /** Once a frame: every running sound up or down to where it should be. */
  function updateSound(dt, paused) {
    if (!ac || ac.state !== 'running') return;
    const live = !paused && gs.phase !== 'title' && gs.phase !== 'end';
    const m = ((gs.t % 1440) + 1440) % 1440;
    const dark = m < 330 || m > 1230;
    const inVan = gs.waitMode === 'van';
    const muffle = inVan ? 0.25 : 1;
    const pumpRun = live && pump.visible && gs.pumpHere && gs.phase === 'pour' && !!gs.truck && !gs.truck.waiting && gs.blocked < 0;
    const pumpIdle = live && pump.visible && gs.pumpHere && !pumpRun;
    loopTo('pump', (pumpRun ? 0.42 : pumpIdle ? 0.1 : 0) * near(pump.position.x, pump.position.z, 60) * muffle, pump.position.x, pump.position.z);
    loopTo('mixer', live && mixer.visible ? 0.3 * near(mixer.position.x, mixer.position.z, 50) * muffle : 0, mixer.position.x, mixer.position.z);
    const running = live && mpos.on && input.action && lastCtxKind === 'trowel';
    const ride = gs.tool === 'rideOn', small = gs.tool === 'trowelSmall';
    loopTo('trowel', live && mpos.on && !gs.fitting ? (running ? 0.32 : 0.12) * (ride ? 1.4 : 1) : 0, mpos.x, mpos.z);
    if (loops._trowelOsc) loops._trowelOsc.frequency.setTargetAtTime((running ? 96 : 58) * (ride ? 0.72 : small ? 1.25 : 1), ac.currentTime, 0.25);
    if (loops._trowelHiss) loops._trowelHiss.gain.setTargetAtTime(running ? (gs.tool === 'pans' ? 0.5 : 0.28) : 0, ac.currentTime, 0.1);
    loopTo('pour', live && stream.visible ? 0.45 : 0);
    const anim = live ? markerAnim() : null;
    loopTo('water', anim === 'wash' ? 0.22 : 0, POS.ibc.x, POS.ibc.z);
    const scraping = live && ((input.action && (lastCtxKind === 'level' || lastCtxKind === 'repair')) || anim === 'edger');
    loopTo('scrape', scraping ? 0.2 : 0);
    loopTo('wind', live ? (0.03 + day.wind * 0.012) * (inVan ? 0.4 : 1) : 0);
    loopTo('rain', live && performance.now() < rainUntil ? 0.3 * muffle : 0);
    loopTo('crickets', live && dark && tempAt(gs.t) > 8 ? 0.015 : 0);
    const u = live && day.boom ? boomUnfold() : 0;
    loopTo('hydraulic', live && day.boom && pump.visible ? clamp(boomSpeed * 0.04 + (u > 0 && u < 1 ? 0.12 : 0), 0, 0.18) : 0, pump.position.x, pump.position.z);
    loopTo('traffic', live ? (dark ? 0.012 : 0.035) * (inVan ? 0.5 : 1) : 0, player.x, 44);
    // the laser receiver on the float or the hose: a steady tone on height, fast beeps high, slow low
    const rx = live && gs.laserOn && laserWorks() && !gs.pourDone && (gs.tool === 'float' || gs.tool === 'hose') && target;
    const dev = rx ? target.fill - day.thick : 0;
    loopTo('rxTone', rx && Math.abs(dev) <= 3 ? 0.03 : 0);
    if (rx && Math.abs(dev) > 3 && performance.now() > rxNext) { rxNext = performance.now() + (dev > 0 ? 120 : 420); sfx('rx'); }
    musicTick();
    if (musicGain) {
      const want = musicOn ? (performance.now() < duckUntil ? 0.18 : 0.55) : 0;
      if (want !== musicWant) { musicWant = want; musicGain.gain.setTargetAtTime(want, ac.currentTime, 0.3); }
    }
    loopTo('engine', live && drives.length ? 0.2 : 0, drives.length ? drives[0].group.position.x : undefined, drives.length ? drives[0].group.position.z : undefined);
    if (live && !dark && day.rh < 85 && !inVan && performance.now() > birdAt) {
      birdAt = performance.now() + rnd(4000, 14000);
      const a = rnd(0, Math.PI * 2);
      sfx('bird', player.x + Math.cos(a) * 25, player.z + Math.sin(a) * 25);
    }
  }

  // ------------------------------------------------------------------ voices
  // The characters say their lines out loud: through the app's text-to-speech inside MixMaster
  // (the WebView has none of its own), or the browser's elsewhere. Only what is in quotes is
  // spoken — the narration stays on the screen. Each character has a pitch and a pace.
  let voicesOn = store('pourday.voices') !== 'off';
  const VOICES = { manager: [0.8, 1.2], foreman: [0.9, 1.1], pump: [0.75, 0.95], truck: [0.85, 1.0], alien: [1.9, 0.75], kid: [1.6, 1.1], plant: [1.1, 1.05] };
  let duckUntil = 0;
  function spoken(text) {
    const q = String(text).match(/"[^"]+"/g);
    return (q ? q.join(' ') : String(text))
      .replace(/"/g, '').replace(/\([^)]*\)/g, '')
      .replace(/\*\*\*/g, ' bleep ').replace(/€\s?(\d+)/g, '$1 euros').replace(/m³/g, ' cubic metres').replace(/m²/g, ' square metres')
      .replace(/(\d)\s?mm\b/g, '$1 millimetres').replace(/(\d)\s?h\b/g, '$1 hours').replace(/(\d)\s?min\b/g, '$1 minutes').replace(/±/g, 'plus or minus ').replace(/°C/g, ' degrees')
      .trim();
  }
  // Each character is cast one of the phone's own voices for the day: a man's voice for the men and
  // a woman's for the women where the phone lets on which is which, and nobody sharing a voice with
  // anybody else while there are voices to go round — so the accents get mixed too.
  const GENDER = { manager: 'm', foreman: 'm', pump: 'm', truck: 'm', plant: 'f', alien: '', kid: '' };
  let voiceBook = null;            // the voices on offer: [{ n: name, l: language, g: 'f' | 'm' | '' }]
  let cast = {};                   // who speaks with which today
  function voicesOnOffer() {
    if (voiceBook && voiceBook.length) return voiceBook;
    let list = [];
    try {
      if (appBridge && typeof appBridge.voices === 'function') list = JSON.parse(appBridge.voices() || '[]');
      else if (window.speechSynthesis) {
        list = window.speechSynthesis.getVoices().filter((v) => /^en/i.test(v.lang))
          .map((v) => ({ n: v.name, l: v.lang, g: /female|woman/i.test(v.name) ? 'f' : /\bmale\b|\bman\b/i.test(v.name) ? 'm' : '' }));
      }
    } catch (e) { list = []; }
    voiceBook = Array.isArray(list) ? list : [];
    return voiceBook;
  }
  function castFor(key, g) {
    if (cast[key]) return cast[key];
    const book = voicesOnOffer();
    if (!book.length) return '';   // the engine isn't up yet: cast them on their next line
    const taken = new Set(Object.values(cast));
    const fits = (v) => !g || v.g === g;
    const pools = [book.filter((v) => fits(v) && !taken.has(v.n)), book.filter((v) => !v.g && !taken.has(v.n)), book.filter(fits), book];
    cast[key] = pick(pools.find((pl) => pl.length)).n;
    return cast[key];
  }
  function newCast() { cast = {}; voiceBook = null; }
  const saidLog = [];              // what was said lately, for the tests
  function say(text, who) {
    if (!voicesOn || !text) return;
    let p = 1, r = 1, key = '', g = '';
    if (Array.isArray(who)) [p, r] = who;
    else if (who && typeof who === 'object') ({ p, r, key, g } = who);
    else if (VOICES[who]) { [p, r] = VOICES[who]; key = who; g = GENDER[who] || ''; }
    const name = key ? castFor(key, g) : '';
    const line = spoken(text);
    if (!line) return;
    saidLog.push(line);
    if (saidLog.length > 40) saidLog.shift();
    duckUntil = performance.now() + line.length * 70 + 600;
    try {
      if (appBridge && typeof appBridge.speakAs === 'function') { appBridge.speakAs(line, p, r, name); return; }
      if (appBridge && typeof appBridge.speak === 'function') { appBridge.speak(line, p, r); return; }
      if (window.speechSynthesis && window.SpeechSynthesisUtterance) {
        const u = new SpeechSynthesisUtterance(line);
        u.lang = 'en-GB'; u.pitch = p; u.rate = r;
        const v = name && window.speechSynthesis.getVoices().find((x) => x.name === name);
        if (v) u.voice = v;
        window.speechSynthesis.cancel();
        window.speechSynthesis.speak(u);
      }
    } catch (e) { /* no voice to be had: the line is on the screen anyway */ }
  }
  function hush() {
    duckUntil = 0;
    try {
      if (appBridge && typeof appBridge.hush === 'function') appBridge.hush();
      else if (window.speechSynthesis) window.speechSynthesis.cancel();
    } catch (e) { /* nothing was talking */ }
  }
  function setVoices(on) { voicesOn = on; store('pourday.voices', on ? 'on' : 'off'); if (!on) hush(); }
  /** A person's voice, kept for the whole of their visit: their own from the phone's, a little higher or lower. */
  let personN = 0;
  function personVoice(g) {
    if (g === 'kid') return { p: 1.55, r: 1.1, key: 'kid', g: '' };
    return { p: rnd(0.85, 1.2), r: rnd(0.95, 1.12), key: 'person' + (++personN), g: g || '' };
  }

  // ------------------------------------------------------------------ the player
  const player = { x: POS.vanDoor.x, z: POS.vanDoor.z, yaw: -2.2, pitch: -0.12, fall: 0, bob: 0, stepAcc: 0, moving: false };
  const input = { keys: {}, jx: 0, jy: 0, action: false, actionTapped: false };

  function cellAt(x, z) {
    if (!gs || x < SLAB.x0 || x >= SLAB.x1 || z < SLAB.z0 || z >= SLAB.z1) return null;
    const c = gs.grid[Math.floor(z - SLAB.z0) * NX + Math.floor(x - SLAB.x0)];
    return c && c.on ? c : null;
  }
  function onSlab(x, z) { return !!cellAt(x, z); }
  function obstacles() {
    const list = [
      { x0: -28.9, x1: -23.3, z0: 7.3, z1: 10.9 },
      { x0: -21.7, x1: -20.3, z0: -9.7, z1: -8.3 },
      { x0: -40, x1: -36, z0: -26, z1: -22 },
      { x0: -37.2, x1: -30.8, z0: 11.6, z1: 14.4 },
      { x0: -31.6, x1: -30.4, z0: -15.6, z1: -14.4 },
    ];
    if (pump.visible) { const w = day.boom ? 2.9 : 1.3; list.push({ x0: pump.position.x - 4.6, x1: pump.position.x + 4.4, z0: pump.position.z - w, z1: pump.position.z + w }); }
    if (mixer.visible) list.push({ x0: mixer.position.x - 4.8, x1: mixer.position.x + 4.6, z0: mixer.position.z - 1.3, z1: mixer.position.z + 1.3 });
    return list;
  }
  function collide(x, z) {
    x = clamp(x, -43.5, 43.5);
    z = clamp(z, -33.5, 33.5);
    for (const o of obstacles()) {
      const pad = 0.35;
      if (x > o.x0 - pad && x < o.x1 + pad && z > o.z0 - pad && z < o.z1 + pad) {
        const dl = x - (o.x0 - pad), dr = o.x1 + pad - x, du = z - (o.z0 - pad), dd = o.z1 + pad - z;
        const m = Math.min(dl, dr, du, dd);
        if (m === dl) x = o.x0 - pad; else if (m === dr) x = o.x1 + pad; else if (m === du) z = o.z0 - pad; else z = o.z1 + pad;
      }
    }
    return [x, z];
  }

  // ------------------------------------------------------------------ marks on the concrete
  // A mark is kept as where it is and how deep it went, and drawn over the surface every time the
  // surface is redrawn — so a machine wearing it down shows it fading, pass by pass, not all at once.
  /** How deep a print goes: to the laces in fresh concrete, a dent at 60%. */
  function markDepth() { return clamp((65 - gs.H) / 45, 0.3, 1); }
  function stamp(kind, x, z, rot, silent, scale) {
    const c = cellAt(x, z);
    if (!c || !gs.poured || gs.H >= 60 || c.fill < 20) return false;
    // enough to read as trampled; more would only make every redraw slower
    if (c.marks.length >= 16) return false;
    c.marks.push({ kind, x, z, rot: rot || 0, depth: markDepth() * (scale || 1) });
    surfDirty = true;
    if (!silent) gs.stats.prints++;
    return true;
  }
  function clearMarks(c) {
    if (!c.marks.length) return false;
    c.marks = [];
    surfDirty = true;
    return true;
  }
  /** Wears the marks in a square down by `amount` of their depth; true when one went for good. */
  function wearMarks(c, amount) {
    if (!c.marks.length || c.defect) return false;
    c.marks.forEach((m) => { m.depth -= amount; });
    const before = c.marks.length;
    c.marks = c.marks.filter((m) => m.depth > 0.05);
    surfDirty = true;
    if (c.marks.length < before && !c.marks.length) { gs.stats.repaired++; return true; }
    return false;
  }
  /** Hand troweling a corner or collar: you trowel your way back out, knees and boots included. */
  function troweledOut(x1, z1, x2, z2) {
    gs.cells.forEach((c) => {
      if (!c.marks.length || c.defect) return;
      const n = c.marks.length;
      c.marks = c.marks.filter((m) => hyp(m.x, m.z, x1, z1) > 0.9 && hyp(m.x, m.z, x2, z2) > 0.9);
      if (c.marks.length !== n) surfDirty = true;
    });
  }
  /** The turn about the vertical that points a mark's toe (drawn towards -z) along dx, dz. */
  function headingOf(dx, dz) { return Math.atan2(-dx, -dz); }
  function stampLine(kind, a, b, spacing) {
    const len = hyp(a.x, a.z, b.x, b.z) || 0.001;
    const ux = (b.x - a.x) / len, uz = (b.z - a.z) / len;
    const rot = headingOf(ux, uz);
    let n = 0;
    for (let s = 0; s <= len; s += spacing) {
      const f = s / len, side = (Math.round(s / spacing) % 2 ? 0.12 : -0.12);
      const x = lerp(a.x, b.x, f) - uz * side, z = lerp(a.z, b.z, f) + ux * side;
      if (stamp(kind, x, z, rot)) n++;
    }
    return n;
  }

  // ------------------------------------------------------------------ the slab, as numbers
  function neighbours(c) {
    const out = [];
    for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) {
      if (!di && !dj) continue;
      const i = c.i + di, j = c.j + dj;
      if (isOn(i, j)) out.push(gs.grid[j * NX + i]);
    }
    return out;
  }
  function filledShare() { return gs.cells.filter((c) => c.fill >= day.thick - 10).length / gs.cells.length; }
  function rms() {
    let s = 0;
    gs.cells.forEach((c) => { const d = c.fill - day.thick; s += d * d; });
    return Math.sqrt(s / gs.cells.length);
  }
  function laserWorks() { return gs.prep.laser && gs.laserBattery; }
  /** How the slab came out: mean thickness against the order, and how wavy it is around its own mean. */
  function slabReport() {
    const n = gs.cells.length || 1;
    const mean = gs.cells.reduce((s, c) => s + c.fill, 0) / n;
    const sd = Math.sqrt(gs.cells.reduce((s, c) => s + (c.fill - mean) * (c.fill - mean), 0) / n);
    const allow = 40 + 20 * gs.pouredM3;
    return { off: mean - day.thick, sd, slowBy: Math.max(0, gs.pourMins - allow) };
  }
  function fillIn(str, v) { return str.replace(/\{(\w)\}/g, (m, k) => (v[k] !== undefined ? v[k] : m)); }
  /** The machine tool in hand, if any, and the flag it leaves on a square it has been over. */
  /** The machine in hand while there is troweling to do, if any. */
  function machineTool() { return gs.phase === 'cure' && isMachine(gs.tool) ? gs.tool : null; }
  function fitted() { return isMachine(gs.tool) ? gs.fit[gs.tool] : null; }
  function passKey() { return fitted() === 'blades' ? 'covB' : 'covP'; }

  // the shape and the colour of the slab
  let cellsDirty = true;
  const WET = new THREE.Color(0.52, 0.53, 0.55), DRY = new THREE.Color(0.97, 0.97, 0.96);
  const DEV = { ok: new THREE.Color(0x4fae6a), hi: new THREE.Color(0xe0873a), vhi: new THREE.Color(0xd8392f), lo: new THREE.Color(0x5aa9ff), vlo: new THREE.Color(0x2f6fd0) };
  function paintCells() {
    const laser = gs.laserOn && laserWorks() && !gs.pourDone;
    const base = tmpC.copy(WET).lerp(DRY, clamp(gs.H / 70, 0, 1));
    const devC = new THREE.Color();
    for (const k of slabVerts) {
      const x = slabPos.getX(k), z = slabPos.getZ(k);
      const f = fillAt(x, z);
      slabPos.setY(k, surfY(f));
      if (laser) {
        const d = f - day.thick;
        devC.copy(Math.abs(d) <= 3 ? DEV.ok : d > 0 ? (d > 10 ? DEV.vhi : DEV.hi) : (d < -10 ? DEV.vlo : DEV.lo));
        devC.lerp(base, 0.25);
        slabCol[k * 3] = devC.r; slabCol[k * 3 + 1] = devC.g; slabCol[k * 3 + 2] = devC.b;
      } else {
        slabCol[k * 3] = base.r; slabCol[k * 3 + 1] = base.g; slabCol[k * 3 + 2] = base.b * 1.01;
      }
    }
    slabPos.needsUpdate = true;
    slabGeo.attributes.color.needsUpdate = true;
    if (skirtGeo) {
      const sp = skirtGeo.attributes.position;
      skirtTop.forEach((v, k) => sp.setY(k * 2, Math.max(0.012, slabPos.getY(v))));
      sp.needsUpdate = true;
    }
    slabGeo.computeVertexNormals();
    // wet concrete gleams, drying concrete goes matt, and the blades bring the shine back
    const shine = !gs.poured ? 1 : gs.H < 30 ? 1 - gs.H / 40 : 0.25;
    const blade = Math.min(3, gs.bladePasses.length);
    slabMat.shininess = gs.poured && gs.H >= 30 ? 8 + blade * 16 : 70;
    slabMat.specular.setScalar(Math.max(shine * 0.32, blade * 0.09));
    cellsDirty = false;
  }

  // the surface as it is shown: what the tools left, the marks on top, and while a machine is
  // running, the squares this pass has not been over yet
  const viewCanvas = document.createElement('canvas');
  viewCanvas.width = surfCanvas.width;
  viewCanvas.height = surfCanvas.height;
  const view = viewCanvas.getContext('2d');
  surfTex.image = viewCanvas;
  function refreshSurface() {
    view.globalAlpha = 1;
    view.drawImage(surfCanvas, 0, 0);
    for (const c of gs.cells) {
      for (const m of c.marks) drawMark(view, m, c.defect);
    }
    const tool = machineTool();
    if (tool && gs.H >= 15) {
      const key = passKey();
      view.globalAlpha = 1;
      view.fillStyle = 'rgba(255,150,40,0.16)';
      for (const c of gs.cells) if (!c[key]) view.fillRect(c.i * PPM, c.j * PPM, PPM, PPM);
      view.fillStyle = 'rgba(255,150,40,0.35)';
      for (const c of gs.cells) {
        if (c[key]) continue;
        // a thin rim on the edge of what is left, so the unfinished part reads as a shape
        const x = c.i * PPM, y = c.j * PPM;
        const done = (i, j) => !isOn(i, j) || gs.grid[j * NX + i][key];
        if (done(c.i - 1, c.j)) view.fillRect(x, y, 3, PPM);
        if (done(c.i + 1, c.j)) view.fillRect(x + PPM - 3, y, 3, PPM);
        if (done(c.i, c.j - 1)) view.fillRect(x, y, PPM, 3);
        if (done(c.i, c.j + 1)) view.fillRect(x, y + PPM - 3, PPM, 3);
      }
    }
    surfTex.needsUpdate = true;
    surfDirty = false;
  }

  // ------------------------------------------------------------------ HUD bits
  const toastBox = $('#toasts');
  function toast(text, kind) {
    const d = document.createElement('div');
    d.className = 'toast' + (kind ? ' ' + kind : '');
    d.textContent = text;
    toastBox.prepend(d);
    while (toastBox.children.length > 3) toastBox.lastChild.remove();
    sfx(kind === 'good' ? 'chime' : kind === 'warn' ? 'buzz' : 'pop');
    setTimeout(() => d.remove(), 6500);
  }

  /** A toast that, once shown, keeps quiet for a while: what you do over and over should not say so every time. */
  const toastLast = {};
  function toastOnce(key, text, kind, quietMs) {
    const now = performance.now();
    if (toastLast[key] && now - toastLast[key] < quietMs) return;
    toastLast[key] = now;
    toast(text, kind);
  }

  const modalQueue = [];
  let modalOpen = null;
  function modal(spec) {
    if (modalOpen) { modalQueue.push(spec); return; }
    modalOpen = spec;
    sfx(spec.sound || 'pop');
    if (spec.voice) say(spec.say || spec.text, spec.voice);
    gs.waitMode = gs.waitMode === 'van' ? 'van' : null;
    if (gs.waitMode !== 'van') gs.fastForward = null;
    input.action = false;
    $('#mWho').textContent = spec.who || '';
    $('#mTitle').textContent = spec.title || '';
    $('#mText').textContent = spec.text || '';
    const box2 = $('#mChoices');
    box2.innerHTML = '';
    (spec.choices || [{ label: 'OK', primary: true }]).forEach((ch) => {
      const b = document.createElement('button');
      b.textContent = ch.label;
      if (ch.primary) b.className = 'primary';
      if (ch.danger) b.className = 'danger';
      b.addEventListener('click', () => {
        $('#modal').hidden = true;
        modalOpen = null;
        if (ch.fn) ch.fn();
        if (!modalOpen && modalQueue.length) modal(modalQueue.shift());
      });
      box2.appendChild(b);
    });
    $('#modal').hidden = false;
  }

  // ------------------------------------------------------------------ time
  function timeScale() {
    if (modalOpen || gs.phase === 'title' || gs.phase === 'end' || gs.phase === 'morning') return 0;
    if (gs.waitMode === 'van') return 25;
    if (gs.waitMode === 'guard') return 8;
    if (gs.fastForward) return 14;
    return { prep: 0.6, pipes: 0.6, pour: 0.45, wash: 0.5, cure: 1.0 }[gs.phase] || 0;
  }
  function awayNow() {
    return gs.waitMode === 'van' || hyp(player.x, player.z, site.mid.x, site.mid.z) > 18;
  }
  function simulate(dm, away) {
    while (dm > 0) {
      const step = Math.min(dm, 2);
      gs.t += step;
      dm -= step;
      if (gs.poured && gs.H < 100) {
        const before = gs.H;
        gs.H = Math.min(100, gs.H + cureRate(gs.t) * stageMul(gs.H) * step);
        milestone(before, gs.H);
        if (Math.floor(before) !== Math.floor(gs.H)) cellsDirty = true;
      }
      if (gs.waitMode === 'van') gs.energy = clamp(gs.energy + 0.12 * step, 0, 100);
      else gs.energy = clamp(gs.energy - 0.04 * step, 0, 100);
      if (gs.truck && gs.truck.waiting) gs.truckWaitPaid += 1.5 * step;
      if (gs.phase === 'pour' && gs.truck && !gs.truck.waiting) gs.pourMins += step;
      if (gs.H >= 99.9 && (gs.phase === 'cure' || gs.phase === 'wash') && !gs.gaveUp && !gs.packing && !slabFinished()) tooLate();
      while (gs.schedule.length && gs.schedule[0].at <= gs.t) {
        const ev = gs.schedule.shift();
        ev.fn(away);
        if (modalOpen) break;
      }
      if (gs.phase === 'cure' || gs.phase === 'wash') {
        if (gs.poured && gs.H < 75 && gs.t >= gs.nextNuisance) {
          gs.nextNuisance = gs.t + rnd(22, 50);
          nuisance(away);
        }
        if (gs.H >= 80) hardenMarks();
      }
      if (modalOpen) break;
    }
  }
  function milestone(before, now) {
    const hit = (x) => before < x && now >= x;
    const note = (key, text) => {
      if (gs.milestones[key]) return;
      gs.milestones[key] = true;
      if (gs.waitMode) { gs.waitMode = null; showWait(); }
      toast(text, 'good');
    };
    if (hit(25)) note('p', 'Hardness 25%: it will carry the pans now. First pass window is open.');
    if (hit(55)) note('b', 'Hardness 55%: blades time. Keep the pans if it still needs flattening.');
    if (hit(92)) note('late', 'Hardness 92%: too hard to close the surface any more.');
    if (hit(95)) note('95', 'Hardness 95%. You may, legally, go home.');
    // at 95% whatever marks are left are in it for good; enough of them and somebody has to tell
    // the manager. That somebody is you.
    if (hit(95) && !gs.managerCalled && marksLeft() > 15) { gs.managerCalled = true; at(now < 100 ? gs.t + 3 : gs.t, managerCall); }
  }
  function marksLeft() { return gs.cells.reduce((n, c) => n + c.marks.length, 0); }
  function managerCall() {
    const n = marksLeft();
    gs.yelled = n;
    remember(`${n} marks set in the slab for good. You called the manager. He had opinions.`);
    modal({
      who: 'The slab, at 95%', title: `${n} marks. For ever.`,
      text: `Footprints, paws, the lot — set in at 95%, and nothing in the van can take them out now. Somebody has to tell the manager before the client does.\n\nThat somebody is you.`,
      choices: [{ label: 'Call the manager', primary: true, fn: () => {
        sfx('ring');
        setTimeout(() => sfx('phoneYell'), 1900);
        modal({
          who: 'The manager, on speaker', title: 'He picks up on the first ring.', sound: 'none', voice: 'manager',
          text: pick(L.managerCall).replace(/\{n\}/g, n),
          choices: [
            { label: 'Hold the phone away from your ear', primary: true, fn: () => toast(pick(L.managerAfter), 'warn') },
            { label: 'Blame the dog', fn: () => toast('"THE DOG IS NOT ON THE PAYROLL." He has a point. The dog would be cheaper.', 'warn') },
            { label: 'Call it a "textured finish"', fn: () => toast('A long silence. Then: "Send me the invoice for the textured finish. Addressed to yourself."', 'warn') },
          ],
        });
      } }],
    });
  }
  function hardenMarks() {
    gs.cells.forEach((c) => {
      if (c.marks.length && !c.defect) { c.defect = true; surfDirty = true; }
    });
  }

  // ------------------------------------------------------------------ nuisances
  /** Somewhere on today's slab. */
  function slabPoint() {
    const c = pick(gs.cells);
    return P(gx(c.i) + rnd(0.1, 0.9), gz(c.j) + rnd(0.1, 0.9));
  }
  // ------------------------------------------------------------------ people and dogs on site
  // They come from somewhere, wander rather than march, stop to look at their phones or sniff
  // things, turn their heads to look at you, and change their minds when shouted at.
  const OUT = 2.2;                       // how far round the slab a polite route keeps
  const SIDES = ['w', 'e', 'n', 's'];
  const OPP = { w: 'e', e: 'w', n: 's', s: 'n' };
  function farPoint(side) {
    const b = site.box;
    if (side === 'w') return P(b.x0 - rnd(12, 16), rnd(b.z0 - 4, b.z1 + 4));
    if (side === 'e') return P(b.x1 + rnd(12, 16), rnd(b.z0 - 4, b.z1 + 4));
    if (side === 'n') return P(rnd(b.x0 - 4, b.x1 + 4), b.z0 - rnd(12, 16));
    return P(rnd(b.x0 - 4, b.x1 + 4), b.z1 + rnd(12, 16));
  }
  /** Just off the slab on a side, where somebody stops to ask. */
  function edgePoint(side) {
    const b = site.box;
    if (side === 'w') return P(b.x0 - 1.4, rnd(b.z0 + 0.5, b.z1 - 0.5));
    if (side === 'e') return P(b.x1 + 1.4, rnd(b.z0 + 0.5, b.z1 - 0.5));
    if (side === 'n') return P(rnd(b.x0 + 0.5, b.x1 - 0.5), b.z0 - 1.4);
    return P(rnd(b.x0 + 0.5, b.x1 - 0.5), b.z1 + 1.4);
  }
  /** A path that wanders: each long leg gets a bend or two, so nobody walks like a train on rails. */
  function wander(pts, amount) {
    const out = [pts[0]];
    for (let k = 1; k < pts.length; k++) {
      const a = pts[k - 1], b = pts[k], len = hyp(a.x, a.z, b.x, b.z) || 0.001;
      const n = Math.min(3, Math.floor(len / 3.5));
      const nx = -(b.z - a.z) / len, nz = (b.x - a.x) / len;
      for (let j = 1; j <= n; j++) {
        const f = j / (n + 1), o = rnd(-amount, amount);
        out.push(P(lerp(a.x, b.x, f) + nx * o, lerp(a.z, b.z, f) + nz * o));
      }
      out.push(b);
    }
    return out;
  }
  /** Straight over the slab, via somewhere in the middle of it, to the far side. */
  function acrossFrom(p, side) { return wander([p, slabPoint(), farPoint(OPP[side])], 0.9); }
  /** The long way round: out to the corner nearest them, along the far side of the box, off. */
  function aroundFrom(p, side) {
    const b = site.box, to = OPP[side];
    const pts = [p];
    if (side === 'w' || side === 'e') {
      const z = p.z < (b.z0 + b.z1) / 2 ? b.z0 - OUT : b.z1 + OUT;
      pts.push(P(side === 'w' ? b.x0 - OUT : b.x1 + OUT, z), P(side === 'w' ? b.x1 + OUT : b.x0 - OUT, z));
    } else {
      const x = p.x < (b.x0 + b.x1) / 2 ? b.x0 - OUT : b.x1 + OUT;
      pts.push(P(x, side === 'n' ? b.z0 - OUT : b.z1 + OUT), P(x, side === 'n' ? b.z1 + OUT : b.z0 - OUT));
    }
    pts.push(farPoint(to));
    return wander(pts, 0.5).map((q, k, arr) => (k > 0 && k < arr.length - 1 && onSlab(q.x, q.z) ? P(q.x + (q.x < site.mid.x ? -1 : 1) * 1.5, q.z) : q));
  }
  /** Laps: round and round over the slab, then away. */
  function lapsFrom(p) {
    const pts = [p];
    for (let k = 0; k < irnd(4, 7); k++) pts.push(slabPoint());
    pts.push(farPoint(pick(SIDES)));
    return pts;
  }
  const walkers = [];
  function spawnWalker(kind, path, speed, opts) {
    const g = kind === 'dog' ? '' : (opts && opts.who && opts.who.g) || (chance(0.5) ? 'f' : 'm');
    const m = kind === 'dog' ? makeDog(opts && opts.cat) : makePerson(Object.assign({ g }, chance(0.35) ? { vest: pick([0xd4f53c, 0xff7a1a]) } : {}));
    if (g === 'kid') m.scale.setScalar(0.72);
    m.position.set(path[0].x, 0, path[0].z);
    m.traverse((o) => { if (o.isMesh) o.castShadow = true; });
    scene.add(m);
    sfx(opts && opts.cat ? 'meow' : kind === 'dog' ? 'bark' : 'voice', path[0].x, path[0].z);
    const w = Object.assign({ kind, m, path, seg: 0, speed, acc: 0, pause: 0, pose: null, heading: 0, shouted: 0, voice: personVoice(g) }, opts || {});
    walkers.push(w);
    return w;
  }
  function reroute(w, path, speed) {
    w.path = path;
    w.seg = 0;
    w.acc = 0;
    if (speed) w.speed = speed;
  }
  /** The walker (or driver) under the crosshair, close enough to shout at or talk to. */
  function walkerInSight() {
    camera.getWorldDirection(tmpV);
    const o = camera.position;
    let best = null, bestA = 9;
    const check = (w, x, y, z) => {
      const dx = x - o.x, dy = y - o.y, dz = z - o.z, d = Math.hypot(dx, dy, dz);
      if (d > 16 || d < 0.4) return;
      const a = Math.acos(clamp((dx * tmpV.x + dy * tmpV.y + dz * tmpV.z) / d, -1, 1));
      if (a < Math.atan((w.kind === 'dog' ? 0.8 : 0.55) / d) + 0.03 && a < bestA) { bestA = a; best = w; }
    };
    walkers.forEach((w) => { if (performance.now() > w.shouted) check(w, w.m.position.x, w.m.position.y + (w.kind === 'dog' ? 0.45 : 1.2), w.m.position.z); });
    if (pumpGuy.visible) check({ kind: 'driver', who: 'pump' }, pumpGuy.position.x, 1.2, pumpGuy.position.z);
    if (mixGuy.visible) check({ kind: 'driver', who: 'mixer' }, mixGuy.position.x, 1.2, mixGuy.position.z);
    return best;
  }
  function shoutAt(w) {
    if (w.kind === 'driver') { const line = pick(w.who === 'pump' ? L.driverTalk : L.mixTalk); toast(line); say(line, w.who === 'pump' ? 'pump' : 'truck'); return; }
    w.shouted = performance.now() + 5000;
    const p = P(w.m.position.x, w.m.position.z);
    if (w.kind === 'dog') {
      if (gs.sausage && !w.cat) {
        gs.sausage = false;
        toast(L.dogSausage, 'good');
        sfx('bark', p.x, p.z);
        reroute(w, [p, farPoint(pick(SIDES))], 6);
        w.state = 'leaving';
        remember('A dog tried for the slab. It left with your sausage instead.');
        return;
      }
      sfx('shout');
      if (w.cat) {
        if (chance(0.5)) { toast(L.cat.stays, 'warn'); w.pause = Math.max(w.pause, 8); return; }
        toast(L.cat.goes);
        w.pause = 0; w.state = 'leaving'; w.afterPause = null;
        reroute(w, [p, farPoint(pick(SIDES))], 2.2);
        return;
      }
      const leaves = chance(w.state === 'laps' ? 0.5 : 0.7);
      if (leaves) { toast(pick(L.dogShoo), 'good'); reroute(w, [p, farPoint(pick(SIDES))], 5.5); w.state = 'leaving'; }
      else { toast(pick(L.dogGame), 'warn'); if (w.state !== 'laps') { gs.stats.dogs++; remember('A dog did laps of the slab. You shouted. It loved it.'); } reroute(w, lapsFrom(p), 4.6); w.state = 'laps'; setTimeout(() => sfx('bark', w.m.position.x, w.m.position.z), 400); }
      w.pause = 0;
      return;
    }
    // a person: told off. Most go round; some hurry on across; some just stop and stare
    gs.stats.hell++;
    sfx('shout');
    setTimeout(() => sfx('voice', w.m.position.x, w.m.position.z), 500);
    const side = w.from || pick(SIDES);
    const r = weighted([[0.5, 'back'], [0.3, 'hurry'], [0.2, 'freeze']]);
    if (r === 'back') { const l = pick(L.shoutBack); toast(l, 'good'); say(l, w.voice); reroute(w, aroundFrom(onSlab(p.x, p.z) ? edgePoint(side) : p, side)); if (onSlab(p.x, p.z)) w.path.unshift(p); }
    else if (r === 'hurry') { const l = pick(L.shoutHurry); toast(l, 'warn'); say(l, w.voice); w.speed *= 1.8; }
    else { toast(pick(L.shoutFreeze), 'warn'); w.pause = 2.2; w.pose = 'look'; }
  }
  /** Somebody at the edge, asking. */
  function askToCross(w) {
    const who = w.who;
    if (hyp(player.x, player.z, w.m.position.x, w.m.position.z) > 30 || awayNow()) {
      // nobody to ask: they help themselves
      gs.stats.crossed++;
      reroute(w, acrossFrom(P(w.m.position.x, w.m.position.z), w.from));
      return;
    }
    w.pause = 999;
    w.pose = 'look';
    const go = (path, speed) => { w.pause = 0; w.pose = null; reroute(w, path, speed); };
    const here = P(w.m.position.x, w.m.position.z);
    modal({
      who: who.who, title: 'Can I cross?', text: who.ask, sound: 'voice', voice: w.voice,
      choices: [
        { label: pick(L.hellLabels), primary: true, fn: () => {
          gs.stats.hell++;
          if (chance(0.72)) { toast(who.back); say(who.back, w.voice); go(aroundFrom(here, w.from), 1.4); }
          else { const l = pick(L.crossAnyway); toast(l, 'warn'); remember(`${who.who}: told where to go, crossed anyway.`); gs.stats.crossed++; go(acrossFrom(here, w.from), 1.5); }
        } },
        { label: 'Walk around, please.', fn: () => {
          if (chance(0.85)) { toast('They walk around, muttering about "concrete people".'); go(aroundFrom(here, w.from), 1.3); }
          else { toast('They hear "around" as "across". It happens.', 'warn'); gs.stats.crossed++; go(acrossFrom(here, w.from), 1.3); }
        } },
        { label: 'Fine. Quickly.', danger: true, fn: () => {
          toast('They are not quick. They stop in the middle to take a phone call.', 'warn');
          remember(`You let ${who.who.toLowerCase()} cross. They took a phone call in the middle.`);
          gs.stats.crossed++;
          const path = acrossFrom(here, w.from);
          go(path, 0.8);
          w.callAt = 2;
        } },
      ],
    });
  }

  // ------------------------------------------------------------------ odd things
  // Visitors that are not people or dogs: a saucer with a message, a football, a cat that picks
  // the middle of the slab for a nap, somebody's drone, a plastic bag with somewhere to be.
  const ufo = (function () {
    const g = new THREE.Group();
    const hull = mesh(new THREE.SphereGeometry(2.2, 28, 12), new THREE.MeshPhongMaterial({ color: 0x9aa3ad, specular: 0xffffff, shininess: 90 }), 0, 0, 0, g);
    hull.scale.y = 0.22;
    mesh(new THREE.SphereGeometry(0.95, 18, 10, 0, Math.PI * 2, 0, Math.PI / 2), new THREE.MeshPhongMaterial({ color: 0x7fd6ff, transparent: true, opacity: 0.75, shininess: 100 }), 0, 0.3, 0, g);
    const lights = [];
    for (let k = 0; k < 12; k++) {
      const a = (k / 12) * Math.PI * 2;
      lights.push(mesh(new THREE.SphereGeometry(0.11, 8, 6), new THREE.MeshBasicMaterial({ color: 0xffffff }), Math.cos(a) * 2.05, 0, Math.sin(a) * 2.05, g));
    }
    const beam = new THREE.Mesh(new THREE.ConeGeometry(2.6, 9, 24, 1, true), new THREE.MeshBasicMaterial({ color: 0x8dff9a, transparent: true, opacity: 0.16, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide }));
    beam.position.y = -4.5;
    g.add(beam);
    g.visible = false;
    scene.add(g);
    return { g, lights, beam };
  })();
  const LIGHT_COLS = [0xff5a5a, 0xffd23f, 0x6bd68a, 0x5aa9ff, 0xd07aff];
  const odd = [];                 // what is visiting right now

  /**
   * Letter positions for a message through the middle of the slab, written to be read from where
   * you are standing: the tops of the letters away from you, the words running to your right.
   * Along whichever of the slab's two ways reads best from there and fits; null if neither fits.
   */
  function layoutMessage(msg, c, strict) {
    let fx = c.x - player.x, fz = c.z - player.z;
    const fl = Math.hypot(fx, fz) || 1;
    fx /= fl; fz /= fl;
    const rx = -fz, rz = fx;                  // your right, looking at the middle of the slab
    const axes = [[Math.sign(rx) || 1, 0], [0, Math.sign(rz) || 1]].sort((a, b) => Math.abs(b[0] * rx + b[1] * rz) - Math.abs(a[0] * rx + a[1] * rz));
    const step = 1.0, n = msg.length;
    for (const [ax, az] of strict ? axes.slice(0, 1) : axes) {
      const out = [];
      let fits = true;
      for (let k = 0; k < n && fits; k++) {
        const o = (k - (n - 1) / 2) * step;
        const x = c.x + ax * o, z = c.z + az * o;
        if (msg[k] === ' ') continue;
        if (!onSlab(x, z)) fits = false;
        else out.push({ ch: msg[k], x, z, rot: -Math.atan2(az, ax) });
      }
      if (fits) return out;
    }
    return null;
  }
  function pickMessage() {
    // the middle if it is slab (a U has no middle), else the middle of some square
    const centres = [site.mid].concat(gs.cells.slice().sort(() => Math.random() - 0.5).slice(0, 12).map((c) => P(gx(c.i) + 0.5, gz(c.j) + 0.5)));
    // readable from where you stand if any message fits that way; sideways if it has to be
    for (const strict of [true, false]) {
      for (const m of L.ufo.msgs.slice().sort(() => Math.random() - 0.5)) {
        for (const c of centres) { const lay = layoutMessage(m, c, strict); if (lay) return { m, lay, c }; }
      }
    }
    return null;
  }
  /** A burned letter: deep, and it only wears out the way any mark does, while it's soft. */
  function burnLetter(l) {
    const c = cellAt(l.x, l.z);
    if (!c) return;
    c.marks.push({ kind: 'glyph', ch: l.ch, x: l.x, z: l.z, rot: l.rot, depth: 1 });
    surfDirty = true;
  }
  function ufoVisit(away) {
    const msg = pickMessage();
    if (!msg) return;
    gs.ufoDone = true;
    gs.ufoMsg = msg.m;
    if (away) {
      msg.lay.forEach(burnLetter);
      const line = L.ufo.away.replace('{m}', msg.m);
      toast(line, 'warn');
      remember(line);
      return;
    }
    const m = msg.c;
    odd.push({ kind: 'ufo', t: 0, msg, from: new THREE.Vector3(m.x - 90, 55, m.z - 50), over: new THREE.Vector3(m.x, 9, m.z), to: new THREE.Vector3(m.x + 140, 95, m.z + 70), burned: 0 });
    toastOnce('ufoLights', 'Lights over the site. Something is coming down.', 'warn', 60000);
  }
  function ballVisit(away) {
    const side = pick(SIDES), pts = [farPoint(side), slabPoint(), farPoint(OPP[side])];
    if (away) { for (let k = 1; k < pts.length; k++) stampLine('ball', pts[k - 1], pts[k], 1.3); toast(L.ball.away, 'warn'); remember(L.ball.away); return; }
    const ball = mesh(new THREE.SphereGeometry(0.11, 12, 10), new THREE.MeshLambertMaterial({ color: 0xf5f5f0, map: ballTex }), pts[0].x, 0.11, pts[0].z);
    ball.castShadow = true;
    odd.push({ kind: 'ball', m: ball, path: pts, seg: 0, acc: 0, dist: 0 });
    sfx('bounce', pts[0].x, pts[0].z);
  }
  const ballTex = (function () {
    const c = document.createElement('canvas');
    c.width = 64; c.height = 32;
    const g = c.getContext('2d');
    g.fillStyle = '#f5f5f0'; g.fillRect(0, 0, 64, 32);
    g.fillStyle = '#1d1f22';
    [[8, 8], [28, 20], [48, 6], [56, 24], [18, 28]].forEach(([x, y]) => { g.beginPath(); g.arc(x, y, 5, 0, Math.PI * 2); g.fill(); });
    const t = new THREE.CanvasTexture(c);
    t.encoding = THREE.sRGBEncoding;
    return t;
  })();
  function catVisit(away) {
    if (away) {
      const p = slabPoint();
      stampLine('paw', farPoint(pick(SIDES)), p, 0.3);
      stamp('curl', p.x, p.z, rnd(0, 6), true);
      toast(L.cat.away, 'warn');
      remember(L.cat.away);
      return;
    }
    const side = pick(SIDES), nap = slabPoint();
    spawnWalker('dog', wander([farPoint(side), edgePoint(side), nap], 0.8), 1.6, {
      cat: true, from: side, state: 'approach',
      onArrive: (w) => {
        w.state = 'napping';
        w.pause = rnd(25, 40);
        w.pose = 'sit';
        stamp('curl', w.m.position.x, w.m.position.z, rnd(0, 6), true);
        toast(L.cat.seen, 'warn');
        w.afterPause = (c) => { c.state = 'leaving'; reroute(c, [P(c.m.position.x, c.m.position.z), farPoint(pick(SIDES))], 1.8); };
      },
    });
  }
  function droneVisit(away) {
    const p = slabPoint();
    if (away) { stamp('crater', p.x, p.z, rnd(0, 6), true); toast(L.drone.away, 'warn'); remember(L.drone.away); return; }
    const g = new THREE.Group();
    box(0.34, 0.08, 0.34, 0x2a2d31, 0, 0, 0, g);
    const rotors = [[-1, -1], [1, -1], [-1, 1], [1, 1]].map(([a, b]) => {
      box(0.28, 0.02, 0.03, 0x3a3d41, a * 0.12, 0, b * 0.12, g).rotation.y = a * b * Math.PI / 4;
      return cyl(0.13, 0.13, 0.008, 0x9aa1a7, a * 0.22, 0.05, b * 0.22, g, 12);
    });
    const from = farPoint(pick(SIDES));
    g.position.set(from.x, 7, from.z);
    scene.add(g);
    odd.push({ kind: 'drone', m: g, rotors, from, to: p, t: 0 });
  }
  function bagVisit(away) {
    const side = pick(SIDES), pts = [edgePoint(side), slabPoint(), edgePoint(OPP[side])];
    if (away) { for (let k = 1; k < pts.length; k++) stampLine('smear', pts[k - 1], pts[k], 0.35); toast(L.bag.away, 'warn'); remember(L.bag.away); return; }
    const bag = box(0.3, 0.22, 0.2, new THREE.MeshLambertMaterial({ color: 0xf2f2ee, transparent: true, opacity: 0.85 }), pts[0].x, 0.2, pts[0].z);
    odd.push({ kind: 'bag', m: bag, path: pts, seg: 0, acc: 0, dist: 0 });
  }
  function updateOdd(dt) {
    for (let k = odd.length - 1; k >= 0; k--) {
      const o = odd[k];
      o.t = (o.t || 0) + dt;
      if (o.kind === 'ufo') {
        const g = ufo.g;
        g.visible = true;
        ufo.lights.forEach((l, n) => l.material.color.setHex(LIGHT_COLS[(n + Math.floor(o.t * 6)) % LIGHT_COLS.length]));
        g.rotation.y += dt * 1.5;
        if (!o.hummed) { o.hummed = true; sfx('ufo'); }
        if (o.t < 4) { const e = 1 - Math.pow(1 - o.t / 4, 3); g.position.lerpVectors(o.from, o.over, e); ufo.beam.visible = false; }
        else if (o.t < 4 + o.msg.lay.length * 0.6 + 2) {
          g.position.set(o.over.x + Math.sin(o.t * 1.3) * 0.3, o.over.y + Math.sin(o.t * 2.1) * 0.25, o.over.z + Math.cos(o.t * 1.1) * 0.3);
          ufo.beam.visible = true;
          const due = Math.floor((o.t - 5) / 0.6);
          while (o.burned < o.msg.lay.length && o.burned <= due) {
            const l = o.msg.lay[o.burned++];
            burnLetter(l);
            sfx('burn', l.x, l.z);
            for (let n = 0; n < 8; n++) emit(l.x + rnd(-0.3, 0.3), groundY(l.x, l.z) + 0.05, l.z + rnd(-0.3, 0.3), rnd(-0.2, 0.2), rnd(0.5, 1.2), rnd(-0.2, 0.2), 1.2, 0x9a9da0, 0.08, 0.3);
          }
          if (o.burned === o.msg.lay.length && !o.spoke) {
            o.spoke = true;
            const line = L.ufo.seen.replace('{m}', o.msg.m);
            toast(line, 'warn');
            remember(line);
            say(pick(L.ufo.voice), 'alien');
          }
        } else if (o.t < 4 + o.msg.lay.length * 0.6 + 4.5) {
          ufo.beam.visible = false;
          const e = Math.pow((o.t - (4 + o.msg.lay.length * 0.6 + 2)) / 2.5, 2);
          g.position.lerpVectors(o.over, o.to, e);
        } else { g.visible = false; odd.splice(k, 1); }
        continue;
      }
      if (o.kind === 'ball' || o.kind === 'bag') {
        const a = o.path[o.seg], b = o.path[o.seg + 1];
        if (!b) {
          scene.remove(o.m); odd.splice(k, 1);
          if (o.kind === 'ball') { toast(L.ball.seen, 'warn'); remember('A football bounced across the slab.'); say(L.ball.kid, 'kid'); } else { toast(L.bag.seen, 'warn'); remember('A plastic bag dragged a line across the slab.'); }
          continue;
        }
        const len = hyp(a.x, a.z, b.x, b.z) || 0.001, speed = o.kind === 'ball' ? 5 : 2.4;
        o.acc += (speed * dt) / len;
        if (o.acc >= 1) { o.acc = 0; o.seg++; continue; }
        const x = lerp(a.x, b.x, o.acc), z = lerp(a.z, b.z, o.acc);
        o.dist += speed * dt;
        if (o.kind === 'ball') {
          const hop = Math.abs(Math.sin((o.dist / 1.3) * Math.PI));
          o.m.position.set(x, groundY(x, z) + 0.11 + hop * 0.45, z);
          o.m.rotation.x += dt * 12;
          if (o.dist > 1.3) { o.dist = 0; if (stamp('ball', x, z, 0, true)) sfx('bounce', x, z); }
        } else {
          o.m.position.set(x, groundY(x, z) + 0.12 + Math.abs(Math.sin(o.t * 5)) * 0.25, z);
          o.m.rotation.set(Math.sin(o.t * 3) * 0.5, o.t * 2, Math.cos(o.t * 4) * 0.4);
          if (o.dist > 0.3) { o.dist = 0; stamp('smear', x, z, headingOf(b.x - a.x, b.z - a.z), true); }
        }
        continue;
      }
      if (o.kind === 'drone') {
        o.rotors.forEach((r) => { r.rotation.y += dt * 40; });
        if (o.t < 5) {
          const e = o.t / 5;
          o.m.position.set(lerp(o.from.x, o.to.x, e) + Math.sin(o.t * 3) * 0.6, 7 + Math.sin(o.t * 2) * 0.5, lerp(o.from.z, o.to.z, e) + Math.cos(o.t * 2.5) * 0.6);
          o.m.rotation.set(Math.sin(o.t * 4) * 0.15, o.t, Math.cos(o.t * 3) * 0.15);
          if (!o.buzz) { o.buzz = true; sfx('drone', o.m.position.x, o.m.position.z); }
        } else if (!o.down) {
          // the battery gives up
          o.m.position.y -= dt * 9;
          o.m.rotation.x += dt * 6;
          const gy = groundY(o.m.position.x, o.m.position.z) + 0.05;
          if (o.m.position.y <= gy) {
            o.down = true;
            o.m.position.y = gy;
            stamp('crater', o.m.position.x, o.m.position.z, rnd(0, 6), true);
            sfx('thud', o.m.position.x, o.m.position.z);
            for (let n = 0; n < 14; n++) emit(o.m.position.x, gy, o.m.position.z, rnd(-1.5, 1.5), rnd(1, 2.5), rnd(-1.5, 1.5), 0.6, 0x85888a, 0.05);
            toast(L.drone.seen, 'warn');
            remember('A drone crashed into the slab.');
            say(L.drone.voice, 'kid');
          }
        } else if (o.t > 30) { scene.remove(o.m); odd.splice(k, 1); }
      }
    }
  }

  function nuisance(away) {
    const mins = ((gs.t % 1440) + 1440) % 1440, dark = mins < 360 || mins > 1170;
    const kinds = [[0.4, 'cross'], [day.dogChance, 'dog'], [0.08, 'bird'], [0.1, 'foreman'], [0.07, 'kid'], [0.08, 'ball'], [0.07, 'cat'], [0.05, 'drone']];
    if (!gs.ufoDone) kinds.push([dark ? 0.14 : 0.05, 'ufo']);
    if (day.wind >= 5) kinds.push([0.08, 'bag']);
    if (day.rh > 76 && !gs.rained) kinds.push([0.12, 'rain']);
    const kind = weighted(kinds);
    if (kind === 'cross') {
      const who = pick(L.cross);
      if (away) {
        if (chance(0.65)) {
          const side = pick(SIDES);
          const p = acrossFrom(farPoint(side), side);
          let n = 0;
          for (let k = 1; k < p.length; k++) n += stampLine('boot', p[k - 1], p[k], 0.72);
          if (n) { gs.stats.crossed++; const line = pick(L.crossAway); toast(line, 'warn'); remember(line); }
        }
        return;
      }
      // they come over from somewhere and stop at the edge to ask
      const side = pick(SIDES);
      spawnWalker('person', wander([farPoint(side), edgePoint(side)], 0.8), rnd(1.1, 1.5), { who, from: side, onArrive: askToCross });
    } else if (kind === 'dog') {
      if (away) {
        if (chance(0.85)) {
          gs.stats.dogs++;
          const pts = lapsFrom(farPoint(pick(SIDES)));
          for (let k = 1; k < pts.length; k++) stampLine('paw', pts[k - 1], pts[k], 0.42);
          const line = pick(L.dog.away);
          toast(line, 'warn');
          remember(line);
        }
        return;
      }
      // a dog heads for the slab, stops at the edge to size it up, then goes for it — unless
      // somebody looks it in the eye and shoos it, or has a sausage
      const side = pick(SIDES);
      spawnWalker('dog', wander([farPoint(side), edgePoint(side)], 1.2), 3.2, {
        from: side, state: 'approach',
        onArrive: (w) => {
          w.state = 'eyeing';
          w.pause = 2.6;
          w.pose = 'bark';
          sfx('bark', w.m.position.x, w.m.position.z);
          w.afterPause = (d) => {
            if (d.state !== 'eyeing') return;
            d.state = 'laps';
            gs.stats.dogs++;
            toast(pick(L.dogGame), 'warn');
            remember('A dog did laps of the slab.');
            reroute(d, lapsFrom(P(d.m.position.x, d.m.position.z)), 4.4);
          };
        },
      });
      toastOnce('dogcoming', 'A dog is heading for the slab. Look at it and shoo it — or throw it something.', 'warn', 60000);
    } else if (kind === 'ufo') { ufoVisit(away);
    } else if (kind === 'ball') { ballVisit(away);
    } else if (kind === 'cat') { catVisit(away);
    } else if (kind === 'drone') { droneVisit(away);
    } else if (kind === 'bag') { bagVisit(away);
    } else if (kind === 'bird') {
      const { x, z } = slabPoint();
      stampLine('paw', { x, z }, { x: x + 0.6, z: z + 0.3 }, 0.2);
      toast(L.bird);
    } else if (kind === 'foreman') {
      if (away) { toast('Three missed calls from the foreman. And one voice message that is just breathing.'); return; }
      modal({
        who: 'Foreman, on the phone', title: 'Ring ring.', text: pick(L.foreman), sound: 'ring', voice: 'foreman',
        choices: [
          { label: 'It\'s basically hard.', fn: () => toast('It is not basically hard. You both know it.') },
          { label: 'Tell the truth.', fn: () => toast('Foreman: "Concrete is just stubborn water." He hangs up.') },
          { label: 'Pretend the signal is bad.', primary: true, fn: () => toast('"Kkkhhh... you\'re... breaking... kkkhh." Flawless.', 'good') },
        ],
      });
    } else if (kind === 'kid') {
      if (away || chance(0.4)) {
        const side = pick(SIDES);
        const p = [edgePoint(side), edgePoint(OPP[side])];
        if (stampLine('line', p[0], p[1], 0.9)) { toast('A kid on a scooter drew a perfect line across the slab. Straight, at least.', 'warn'); remember('A scooter kid left a line across the slab.'); }
        return;
      }
      toast('A kid on a scooter eyes the slab. You give the Look. The kid turns around. You still have it.', 'good');
    } else if (kind === 'rain') {
      gs.rained = true;
      if (!away) rainUntil = performance.now() + 30000;
      const pit = () => {
        let n = 0;
        for (let k = 0; k < 12; k++) { const p = slabPoint(); if (stamp('rain', p.x, p.z, rnd(0, 6))) n++; }
        if (n) { toast('Rain. The surface looks like the moon now. Float it out while you still can.', 'warn'); remember('It rained on the fresh slab.'); }
      };
      if (away) { pit(); return; }
      modal({
        who: 'The sky', title: 'Dark clouds.', text: 'Rain in ten minutes. The plastic sheet is in the van, folded by someone who hated you.',
        choices: [
          { label: 'Cover the slab (20 min)', primary: true, fn: () => { gs.energy = clamp(gs.energy - 8, 0, 100); simulate(20, true); toast('Covered. The sheet flapped you in the face twice. The slab is fine.', 'good'); } },
          { label: 'It\'ll pass.', danger: true, fn: () => (chance(0.6) ? pit() : toast('It passed. Lucky. Don\'t get used to it.', 'good')) },
        ],
      });
    }
  }

  // ------------------------------------------------------------------ falling over
  function fall(text) {
    if (player.fall > 0) return;
    player.fall = 1.8;
    gs.stats.falls++;
    gs.energy = clamp(gs.energy - 5, 0, 100);
    const c = cellAt(player.x, player.z);
    sfx('thud');
    if (c && (gs.phase === 'pour' || gs.H < 30)) sfx('splash');
    if (c && gs.phase === 'pour') { c.fill = Math.max(0, c.fill - 12); cellsDirty = true; }
    else if (c) stamp('butt', player.x, player.z, player.yaw, true);
    const line = text || pick(L.falls);
    toast(line, 'warn');
    remember(line);
  }

  // ------------------------------------------------------------------ the day, phase by phase
  function startDay() {
    $('#title').hidden = true;
    $('#hud').hidden = false;
    // in case a day was left halfway through throwing the tools in the van
    $('#buttons').style.visibility = '';
    $('#targetInfo').style.visibility = '';
    vanHole.visible = vanDoorPanel.visible = false;
    gs.phase = 'morning';
    const alarm = pick(L.alarm);
    const driveTo = (snooze) => {
      const d = pick(L.drive);
      gs.t = 5 * 60 + 25 + snooze + d[1];
      modal({
        who: 'On the way', title: 'The drive in', text: d[0],
        choices: [{ label: 'Arrive', primary: true, fn: () => arrive() }],
      });
    };
    modal({
      who: 'Alarm', title: 'Rise and shine.', text: alarm, sound: 'alarm',
      choices: [
        { label: 'Get up', primary: true, fn: () => driveTo(0) },
        { label: 'Snooze (9 min)', fn: () => driveTo(chance(0.5) ? 9 : 27) },
        { label: 'Snooze and pretend it\'s Sunday', danger: true, fn: () => { remember('You pretended it was Sunday. It was not Sunday.'); driveTo(irnd(45, 75)); } },
      ],
    });
  }

  function arrive() {
    gs.phase = 'prep';
    gs.arrived = gs.t;
    player.x = POS.vanDoor.x; player.z = POS.vanDoor.z;
    player.yaw = Math.atan2(player.x - 0, player.z - 0);
    gs.pumpAt = 7 * 60 + day.pumpDelay;
    const late = gs.t - 6 * 60;
    if (gs.t >= gs.pumpAt) {
      gs.pumpAt = gs.t + 2;
      toast('The pump driver is already here, arms crossed, eating something. You are late.', 'warn');
      remember(`You arrived at ${clock(gs.t)}, after the pump. The pump driver will tell this story at his wedding.`);
    } else if (late > 15) {
      toast(`You arrive at ${clock(gs.t)}. Not first on site. The birds are disappointed.`, 'warn');
    } else {
      toast(pick(L.arrive));
    }
    at(gs.pumpAt, pumpArrives);
    if (day.pumpDelay >= 25 && gs.t < 7 * 60 - 5) {
      at(6 * 60 + 50, () => toast(pick(L.pumpLate).replace('{eta}', clock(gs.pumpAt)), 'warn'));
    }
    buildPrepMarkers();
  }

  function buildPrepMarkers() {
    addMarker('unload', POS.vanDoor, 'Unload tools', 2.2, () => !gs.prep.unload, () => {
      gs.prep.unload = true; gs.toolsUnloaded = true;
      unloadTools();
      sfx('door', POS.vanDoor.x, POS.vanDoor.z);
      sfx('clank', POS.vanDoor.x, POS.vanDoor.z);
      toast('Tools out on the blue tarp: float, hand trowel, hammer, pliers and wire, rebar cutter. The trowels stand next to it.' + (day.area > 50 ? ' The ride-on came too.' : '') + ' Look at one and press Pick up.');
    });
    site.forms.forEach((f, k) => {
      addMarker('form' + k, f, 'Check formwork', 1.8, () => !gs.prep.form[k] && !gs.pourStarted, () => {
        gs.prep.form[k] = true;
        toast(pick(f.run === site.weak ? L.formWeak : L.formOk), f.run === site.weak ? 'good' : '');
      }, { tool: 'hammer' });
    });
    addMarker('laser', POS.tripod, 'Set up laser', 2.6, () => !gs.prep.laser, () => {
      if (!gs.prep.unload) { toast('The laser is still in the van. Unload the tools first.', 'warn'); return false; }
      gs.prep.laser = true;
      tripod.visible = true;
      sfx('beep', POS.tripod.x, POS.tripod.z);
      toast(`Laser set to ${day.thick} mm above the base. It spins. You feel like a scientist.`, 'good');
      return true;
    }, { tool: 'hands' });
    site.ties.forEach((t, k) => {
      addMarker('tie' + k, t, 'Tie the mesh', 1.6, () => !t.done && !gs.pourStarted, () => {
        t.done = true;
        t.bar.visible = false;
        t.twist.visible = true;
        const left = site.ties.filter((x) => !x.done).length;
        toast(pick(L.tie) + (left ? ` ${left} to go.` : ' That\'s the mesh tied.'));
      }, { tool: 'pliers', w: 2.2 });
    });
    site.cuts.forEach((t, k) => {
      addMarker('cut' + k, t, 'Cut the bar', 1.2, () => !t.done && !gs.pourStarted, () => {
        t.done = true;
        t.bar.scale.y = 0.08;
        t.bar.position.y = rebarY() + 0.03;
        sfx('snip', t.x, t.z);
        toast(pick(L.cut));
      }, { tool: 'cutter', w: 2.2 });
    });
  }

  function driveIn(group, target, seconds, done) {
    group.visible = true;
    drives.push({ group, from: group.position.x, to: target, t: 0, seconds, done: () => {
      sfx('brake', group.position.x, group.position.z);
      setTimeout(() => sfx('reverse', group.position.x, group.position.z), 700);
      if (done) done();
    } });
  }
  const drives = [];

  function pumpArrives() {
    gs.pumpHere = true;
    gs.fastForward = null;
    const boom = !!day.boom;
    pump.position.set(60, 0, boom ? BOOM_AT.z : POS.pump.z);
    boomLabel.visible = boom;
    pumpLabel.visible = !boom;
    driveIn(pump, boom ? BOOM_AT.x : POS.pump.x, 5, () => {
      pumpGuy.visible = true;
      if (boom) {
        // beside the truck with the remote round his neck
        pumpGuy.position.set(BOOM_AT.x + 1.2, 0, BOOM_AT.z + 3.6);
        pumpGuy.rotation.y = -Math.PI / 2 - 0.2;
        sfx('brake', BOOM_AT.x, BOOM_AT.z);
      } else {
        pumpGuy.position.set(POS.pump.x - 5.2, 0, POS.pump.z + 1.8);
        pumpGuy.rotation.y = -Math.PI / 2 - 0.4;
      }
    });
    if (gs.phase === 'prep') gs.phase = 'pipes';
    if (boom) {
      gs.boomSetAt = gs.t + 2;
      modal({ who: 'Pump driver', title: 'The boom pump is here.', voice: 'pump', text: pick(L.pumpArrive) + '\n\nNo pipes to carry today. He puts the legs down and swings the boom over the slab; the hose hangs off the end and he follows you with it on his remote. Mostly.', choices: [{ label: 'Lovely', primary: true }] });
      at(gs.t + 12, () => {
        gs.pipes = 6;
        gs.tools.hose = { in: 'ground', x: SLAB.x1 - 1.5, z: gz(ENTRY.j) + 0.5, yaw: Math.PI / 2 };
        toast('Boom up, hose hanging over the slab. Now we wait for the mixer.', 'good');
        maybeStartPour();
      });
    } else {
      pile.visible = true;
      modal({ who: 'Pump driver', title: 'The pump is here.', voice: 'pump', text: pick(L.pumpArrive) + '\n\nLay the pipe line from the pump to the slab: grab a pipe from the pile, carry it to the next marker, clamp it.', choices: [{ label: 'On it', primary: true }] });
      buildPipeMarkers();
    }
    const first = Math.max(7 * 60 + 30 + day.truckDelays[0], gs.t + 25);
    scheduleTruck(first);
    if (first - (7 * 60 + 30) >= 20) at(Math.max(gs.t + 5, 7 * 60 + 25), () => toast(pick(L.truckLate).replace('{eta}', clock(first)), 'warn'));
  }

  function buildPipeMarkers() {
    addMarker('pile', POS.pile, 'Grab a pipe', 1.2, () => gs.pipes < 6 && !gs.carrying, () => {
      gs.carrying = 'pipe';
      sfx('clank', POS.pile.x, POS.pile.z);
      toast(gs.pipes === 0 ? 'A pipe: 3 m of steel and regret. Carry it to the first marker by the pump.' : 'Another one. Your shoulder remembers the last.');
    }, { tool: 'hands' });
    PIPE_ROUTE.forEach((p, k) => {
      addMarker('pipe' + k, p, `Pipe ${k + 1} of 6`, 1.6, () => gs.carrying === 'pipe' && gs.pipes === k, () => {
        gs.carrying = null;
        gs.pipes++;
        sfx('clank', p.x, p.z);
        pipeMeshes.push(pipeBetween(k === 0 ? POS.pumpOut : PIPE_ROUTE[k - 1], p));
        toast(pick(L.pipe));
        gs.energy = clamp(gs.energy - 2, 0, 100);
        if (gs.pipes === 6) {
          // the rubber end hose goes on the last pipe and lies at the edge of the slab
          gs.tools.hose = { in: 'ground', x: SLAB.x1 - 0.8, z: gz(ENTRY.j) + 0.5, yaw: Math.PI / 2 };
          toast('Line laid, pump to slab, end hose on. Now we wait for the mixer.', 'good');
          pile.visible = false;
          maybeStartPour();
        }
      }, { w: 2.2 });
    });
  }

  function scheduleTruck(when) {
    const no = gs.truckNo + 1;
    gs.nextTruckAt = when;
    at(when, () => truckArrives(no));
    if (when - gs.t >= 30 && no > 1) toast(pick(L.truckLate).replace('{eta}', clock(when)), 'warn');
  }

  function truckArrives(no) {
    gs.truckNo = no;
    gs.truck = { no, left: loadOf(no), waiting: true };
    gs.fastForward = null;
    mixer.position.set(70, 0, day.boom ? BOOM_AT.z : POS.mixer.z);
    mixer.rotation.y = day.boom ? Math.PI : 0;
    driveIn(mixer, day.boom ? BOOM_AT.x + 8.3 : POS.mixer.x, 5);
    if (no === 1) {
      // a load that is already a problem doesn't also get to be stiff or soupy
      if (day.trouble) day.mix = 'ok';
      gs.mixState = day.mix;
      gs.mixFactor = day.mix === 'soup' ? 0.75 : day.mix === 'stiff' ? 1.08 : 1;
    }
    toast(`Truck ${no} is here with ${gs.truck.left.toFixed(1)} m³.` + (gs.pipes < 6 ? ' The line isn\'t laid. The driver starts a waiting-time clock at 90 €/h.' : ''), gs.pipes < 6 ? 'warn' : '');
    if (day.trouble && no === day.troubleTruck && !gs.troubleDone) { gs.troubleDone = true; truckTrouble(); return; }
    maybeStartPour();
  }
  /** The truck that is empty, or full of the wrong thing. */
  function truckTrouble() {
    const load = gs.truck.left;
    const another = (min, max) => {
      gs.extraTrucks++;
      gs.extraLoad = load;
      gs.truck = null;
      drives.push({ group: mixer, from: mixer.position.x, to: 90, t: 0, seconds: 7, done: () => { mixer.visible = false; } });
      scheduleTruck(gs.t + irnd(min, max));
    };
    if (day.trouble === 'empty') {
      remember('The mixer came empty.');
      modal({
        who: 'Mixer driver', title: 'The drum is empty.', text: pick(L.emptyTruck) + '\n\nThe plant can send another one.', voice: 'truck',
        choices: [{ label: 'Get on the phone to the plant', primary: true, fn: () => { toast('The plant: "Another one\'s on its way." Somebody at the plant is laughing.', 'warn'); another(45, 80); } }],
      });
      return;
    }
    const w = L.wrong[day.wrongKind];
    modal({
      who: `Truck ${gs.truck.no}`, title: w.title, text: w.text, voice: 'truck',
      choices: [
        { label: 'Send it back', primary: true, fn: () => { toast('Back it goes. The right load is 50 to 90 minutes away. The waiting starts now.', 'warn'); remember(`Truck ${gs.truck.no} brought the wrong concrete. You sent it back.`); another(50, 90); } },
        { label: 'Pour it anyway', danger: true, fn: () => {
          gs.wrongLoad = day.wrongKind;
          remember(w.used);
          if (day.wrongKind === 'screed') { gs.mixState = 'soup'; gs.mixFactor = 0.72; }
          if (day.wrongKind === 'grade') gs.mixFactor *= 0.92;
          toast('Your call. Your name is on the delivery note now.', 'warn');
          maybeStartPour();
        } },
      ],
    });
  }

  function maybeStartPour() {
    if (!gs.truck || gs.pipes < 6) return;
    gs.truck.waiting = false;
    if (gs.pourStarted) { toast(`Truck ${gs.truck.no} backs up to the pump. Keep going.`, 'good'); return; }
    gs.pourStarted = true;
    gs.phase = 'pour';
    const mixText = {
      stiff: 'It comes down the chute like wet sand. Stiff. The pump is going to hate this.',
      ok: 'It flows like it should. Nobody trust it.',
      soup: 'It comes out like soup. Somebody at the plant was generous with the tap. This will take ages to harden.',
    }[day.mix];
    const choices = [];
    if (day.mix === 'stiff') {
      choices.push({ label: 'Add a splash of water', fn: () => { gs.water = 1; gs.mixFactor = 0.9; gs.mixState = 'ok'; toast('The driver adds "a splash". It is a bucket. Pumps easier, hardens slower.'); } });
      choices.push({ label: 'Pump it as it is', primary: true, fn: () => toast('Brave. Keep the hammer handy.', 'warn') });
    } else if (day.mix === 'soup') {
      choices.push({ label: 'Send it back (costs time)', fn: () => {
        gs.truck.waiting = true;
        gs.mixState = 'ok';
        gs.mixFactor = 1;
        const back = irnd(60, 100);
        toast(`The driver goes back to the plant, swearing in two languages. New load in ${back} minutes.`, 'warn');
        remember('You sent a truck of soup back to the plant.');
        gs.extraTrucks++;
        gs.extraLoad = gs.truck.left;
        gs.truck = null;
        mixer.visible = false;
        scheduleTruck(gs.t + back);
      } });
      choices.push({ label: 'Use it anyway', primary: true, fn: () => { toast('Soup it is. It levels itself, and it will cure like it has all the time in the world.', 'warn'); remember('You poured soup. It took its time.'); } });
    } else {
      choices.push({ label: 'Pour!', primary: true });
    }
    modal({
      who: `Mixer driver · ${volumeNeeded().toFixed(1)} m³ needed`, title: 'The concrete is here.', voice: 'truck',
      text: pick(L.truckDriver) + '\n\n' + mixText + `\n\n${day.boom ? 'Pick up the hose hanging off the boom, over the east edge — the boom follows you' : 'Pick up the hose at the end of the line, on the east edge'} — and look at where it goes. Float it to the laser with the float from the tarp. Aim for ${day.thick} mm everywhere. Don't pour more than you need: ${orderedM3().toFixed(1)} m³ ordered, in ${trucksOrdered()} ${trucksOrdered() > 1 ? 'trucks' : 'truck'}.`,
      choices,
    });
  }

  function truckEmpty() {
    const ordered = trucksOrdered() + gs.extraTrucks;
    gs.truck = null;
    setTimeout(() => { mixer.visible = false; }, 0);
    if (filledShare() >= 0.97) { toast('That was the last of it. Level it up and finish the pour.', 'good'); return; }
    if (gs.truckNo < ordered) {
      const delay = day.truckDelays[Math.min(gs.truckNo, day.truckDelays.length - 1)];
      scheduleTruck(gs.t + 12 + delay);
      toast(`Truck ${gs.truckNo} is empty. The next one is due at ${clock(gs.nextTruckAt)}. Float what you have.`);
      return;
    }
    modal({
      who: 'The plant', title: 'Out of concrete.', voice: 'plant',
      text: `You're short. The plant can send one more truck, "in about an hour and a half, the driver is on his lunch".`,
      choices: [
        { label: 'Order one more truck', primary: true, fn: () => { gs.extraTrucks++; gs.extraLoad = Math.max(1, Math.ceil(gs.cells.reduce((sum, c) => sum + Math.max(0, day.thick - c.fill), 0) / 1000 * 1.1 * 2) / 2); remember('You ran out of concrete and ordered an extra truck.'); scheduleTruck(gs.t + irnd(70, 110)); } },
        { label: 'Make do with what\'s there', danger: true, fn: () => toast('Bold. The low spots will remember this.', 'warn') },
      ],
    });
  }

  function finishPour() {
    const share = filledShare();
    const dev = rms();
    const left = gs.truck ? gs.truck.left : 0;
    gs.waste += left;
    gs.pourDone = true;
    gs.poured = true;
    gs.pourEnd = gs.t;
    gs.phase = 'wash';
    if (gs.tool === 'hose') gs.tool = 'hands';
    if (gs.tools.hose) gs.tools.hose.in = 'gone';
    gs.laserOn = false;
    site.ties.filter((t) => !t.done).forEach((t) => { gs.grid[t.j * NX + t.i].defect = true; });
    site.cuts.filter((t) => !t.done).forEach((t) => { gs.grid[t.j * NX + t.i].defect = true; });
    if (site.ties.some((t) => !t.done)) remember('The mesh floated up where it wasn\'t tied. There\'s rebar showing at the surface.');
    if (site.cuts.some((t) => !t.done)) remember('A bar sticks out of the finished slab. Somebody will trip on it for years.');
    gs.truck = null;
    mixer.visible = false;
    gs.nextNuisance = gs.t + rnd(14, 28);
    cellsDirty = true;
    if (share < 0.97) gs.cells.forEach((c) => { if (c.fill < day.thick - 10) c.defect = true; });
    remember(`Pour finished at ${clock(gs.t)}, ±${dev.toFixed(1)} mm off the laser.`);
    modal({
      who: 'Pour done', title: `Poured at ${clock(gs.t)}.`,
      text: `Average error: ±${dev.toFixed(1)} mm.` + (left > 0.2 ? `\n${left.toFixed(1)} m³ left in the truck. The driver dumps it behind the site office. That's where the foreman parks.` : '') +
        `\n\nWash your tools at the water tank before they set. The pump driver does his own pipes.`,
      choices: [{ label: 'To the tank', primary: true }],
    });
    at(gs.t + 6, () => { toast(pick(L.wash)); });
    const rep = slabReport(), v = { d: Math.abs(rep.off).toFixed(0), t: day.thick, r: rep.sd.toFixed(1), h: dur(gs.pourMins) };
    const moan = [];
    if (rep.off < -4) moan.push(fillIn(pick(L.badPour.thin), v));
    if (rep.off > 5) moan.push(fillIn(pick(L.badPour.thick), v));
    if (rep.sd > 5) moan.push(fillIn(pick(L.badPour.bumpy), v));
    if (rep.slowBy > 10) moan.push(fillIn(pick(L.badPour.slow), v));
    if (moan.length) {
      at(gs.t + 4, () => {
        remember('The foreman called about the pour. Words like "pay" and "cut" were used.');
        modal({
          who: 'Phone · the foreman', title: moan.length > 1 ? 'He has a list.' : 'He has a point.', sound: 'ring', voice: 'foreman',
          text: moan.join('\n\n') + '\n\n' + L.badPour.close,
          choices: [
            { label: 'Yes, boss.', primary: true, fn: () => toast('"Yes boss" is free. It\'s the only thing today that was.') },
            { label: 'Blame the laser', fn: () => toast('The laser has a lawyer. You do not.', 'warn') },
            { label: 'Pretend the line is breaking up', fn: () => { toast('"You\'re… breaking… up." He texts instead: "PAY CUT." In capitals.', 'warn'); sfx('buzz'); } },
          ],
        });
      });
    }
    at(gs.t + 8, () => { if (day.boom) gs.boomFoldAt = gs.t; });
    at(gs.t + 10, () => { pipeGroup.clear(); gs.pipesGone = 1; });
    at(gs.t + 16, () => {
      pumpGuy.visible = false;
      drives.push({ group: pump, from: pump.position.x, to: 70, t: 0, seconds: 6, done: () => { pump.visible = false; } });
      toast('The pump leaves, honking twice. Once for goodbye, once for you personally.');
      sfx('honk', pump.position.x, pump.position.z);
    });
  }

  function buildLateMarkers() {
    addMarker('wash', POS.ibcFront, 'Wash tools', 4, () => gs.phase === 'wash', () => {
      gs.washed = true;
      gs.phase = 'cure';
      toast('Tools washed. You washed your boots too, then stepped in the slurry. Classic.');
      modal({
        who: 'Now it hardens', title: 'The waiting part.',
        text: `It's ${Math.round(tempAt(gs.t))} °C with ${day.rh}% humidity and a ${day.thick} mm slab of ${day.area} m². Keep an eye on the hardness meter.\n\n` +
          `• Pans from about 25%: take a power trowel from beside the tarp — they come with pans on. One to three passes.${day.area > 50 ? ' The ride-on does a big slab in half the time.' : ''}\n• Blades from about 55%: fit them on the machine (button next to Put down), then pass again.\n• Corners and pipe collars with the hand trowel; straight edges with the hand trowel or the small edge trowel.\n• Pack up the laser and put it in the van.\n• Nobody leaves before 95%.\n\n` +
          'Meanwhile: guard the slab, nap in the van, or walk to the kebab stand.\n\nFootprints: the float takes them out under 50%, the hand trowel under 70%, the machines up to about 80%. After that they\'re in it for good.',
        choices: [{ label: 'Right', primary: true }],
      });
    });
    addMarker('batteries', POS.vanDoor, 'Get batteries', 1.4, () => !gs.laserBattery && !gs.pourDone, () => {
      gs.laserBattery = true;
      toast('Fresh batteries. The laser beeps like nothing happened. You know what happened.', 'good');
    });
    // the laser goes back in its case, the receiver with it: nothing left to beep at
    addMarker('laserPack', POS.tripod, 'Pack up the laser', 2.4, () => gs.pourDone && gs.prep.laser && !gs.laserPacked && gs.carrying !== 'laser' && tripod.visible, () => {
      tripod.visible = false;
      gs.carrying = 'laser';
      gs.laserOn = false;
      sfx('clank', POS.tripod.x, POS.tripod.z);
      toast('Laser off, tripod folded. To the van with it, receiver and all.');
    }, { tool: 'hands' });
    addMarker('laserVan', P(POS.vanDoor.x + 0.8, POS.vanDoor.z - 1.2), 'Laser in the van', 1.2, () => gs.carrying === 'laser', () => {
      gs.carrying = null;
      gs.laserPacked = true;
      sfx('door', POS.vanDoor.x, POS.vanDoor.z);
      toast('Laser in its case, the receiver in the glovebox. No more beeping today.', 'good');
    });
    addMarker('lunch', POS.kioskFront, 'Lunch', 1.2, () => gs.phase === 'cure' && gs.H < 90, () => { lunch(); });
    addMarker('home', POS.vanDoor, 'Go home', 1.2, () => !gs.packing && ((gs.phase === 'cure' && (gs.panPasses.length > 0 || gs.gaveUp) && (gs.carrying !== 'laser' || gs.gaveUp)) || (gs.phase === 'wash' && gs.gaveUp)), () => { tryGoHome(); });
    site.edges.forEach((e, k) => {
      // straight edges take the small machine or the hand trowel; corners and collars only the hand
      const straight = e.kind === 'edge';
      addMarker('edge' + k, e, e.label, () => (gs.tool === 'trowelSmall' ? 1.0 : 2.2), () => gs.phase === 'cure' && gs.H >= 18 && !e.done, () => {
        if (gs.H < 25) { toastOnce('edgesoft', 'Too soft. You\'re drawing in it, not troweling it. Give it a bit.', 'warn', 20000); return false; }
        e.done = true;
        gs.edgesDone++;
        troweledOut(e.x, e.z, player.x, player.z);
        const left = site.edges.length - gs.edgesDone;
        if (gs.H > 85) { gs.edgeNotes.push('late'); toast(`${e.label}: too hard to close properly. It'll do. It won't be pretty.`, 'warn'); }
        else toastOnce('edge', `${e.label} done. ${left ? `${left} to go.` : 'That\'s all the edges.'}`, 'good', 8000);
        return true;
      }, { w: 2.2, tool: straight ? ['handTrowel', 'trowelSmall'] : 'handTrowel', byMachine: straight });
    });
  }

  function lunch() {
    modal({
      who: 'Kebab & Coffee', title: 'What\'ll it be?', text: L.lunch,
      choices: [
        { label: 'Kebab, extra garlic (40 min)', primary: true, fn: () => { gs.energy = clamp(gs.energy + 45, 0, 100); simulate(40, true); toast('Kebab. The garlic will guard the slab for you for the rest of the day.', 'good'); remember('Kebab with extra garlic.'); } },
        { label: 'Sausage in a bun, one to go (30 min)', fn: () => { gs.energy = clamp(gs.energy + 32, 0, 100); gs.sausage = true; simulate(30, true); toast('You keep one sausage "for later". Later has plans for it.', 'good'); } },
        { label: 'Coffee and a thermos refill (10 min)', fn: () => { gs.energy = clamp(gs.energy + 12, 0, 100); gs.cups = 3; simulate(10, true); toast('Thermos full. You are, again, a person.', 'good'); } },
        { label: 'Nothing. Back to the slab.' },
      ],
    });
  }

  function tryGoHome() {
    if (gs.packing) return false;
    if (gs.gaveUp || (gs.H >= 99.9 && !slabFinished())) { gs.gaveUp = true; packUp(); return true; }
    const missing = [];
    if (gs.H < 95) missing.push(`It's only ${Math.floor(gs.H)}% hard. 95% or you sleep here.`);
    if (!gs.panPasses.length) missing.push('No pan pass yet.');
    if (!gs.bladePasses.length) missing.push('No blade pass yet.');
    if (gs.edgesDone < site.edges.length) missing.push(`${site.edges.length - gs.edgesDone} edges, corners or collars still to trowel.`);
    if (gs.prep.laser && !gs.laserPacked) missing.push('The laser is still out. Pack it up and put it in the van.');
    if (missing.length) {
      modal({ who: 'Foreman, in your head', title: 'Not yet.', text: missing.join('\n'), choices: [{ label: 'Fine', primary: true }] });
      return false;
    }
    endDay();
    return true;
  }

  /** The slab's work is done: pans, blades, and every edge. (The laser is only a tool.) */
  function slabFinished() { return gs.panPasses.length > 0 && gs.bladePasses.length > 0 && gs.edgesDone >= site.edges.length; }
  /** 100% and not finished: nothing more will go on this slab today. */
  function tooLate() {
    gs.gaveUp = true;
    const what = [];
    if (!gs.panPasses.length) what.push('no pan pass');
    if (!gs.bladePasses.length) what.push('no blade pass');
    if (gs.edgesDone < site.edges.length) what.push(`${site.edges.length - gs.edgesDone} edges still rough`);
    remember('It went to 100% before the slab was done.');
    modal({
      who: 'Hardness 100%', title: 'It\'s gone off.', sound: 'buzz', voice: 'foreman',
      text: L.tooLate.text.replace('{what}', what.join(', ')),
      choices: [
        { label: 'Throw everything in the van', primary: true, fn: () => packUp() },
        { label: 'Stare at it a bit longer', fn: () => toast(pick(L.tooLate.stare), 'warn') },
      ],
    });
  }

  // ------------------------------------------------------------------ throwing it all in the van
  // You march to the van and throw the tools in, one after another, the machines last. Each flies
  // from your hand in an arc and lands in the van with a noise; the van rocks. Then the door, the
  // engine, and the day is over.
  // the van's side door faces north-ish; the tools go through it, and you throw from out in front of it
  const VAN_SIDE = new THREE.Vector3(Math.sin(0.25), 0, -Math.cos(0.25));
  const VAN_IN = new THREE.Vector3(POS.van.x + VAN_SIDE.x * 0.7 + 0.3, 1.35, POS.van.z + VAN_SIDE.z * 0.7);
  const VAN_THROW = P(POS.van.x + VAN_SIDE.x * 6.2 + 1.2, POS.van.z + VAN_SIDE.z * 6.2);
  // the side door, slid open: a dark hole in the side of the van, and the door panel slid back
  const vanHole = box(1.35, 1.5, 0.02, 0x131518, 0.3, 1.25, -1.045, van);
  const vanDoorPanel = box(1.35, 1.5, 0.03, 0xdcdad4, -1.15, 1.25, -1.07, van);
  vanHole.visible = vanDoorPanel.visible = false;
  let vanRock = 0;
  function packUp() {
    if (gs.packing) return;
    if (held()) putDown(true);
    gs.waitMode = null; gs.fastForward = null; showWait();
    // stand back from the van, facing the door, which is open
    player.x = VAN_THROW.x; player.z = VAN_THROW.z;
    vanHole.visible = vanDoorPanel.visible = true;
    $('#buttons').style.visibility = 'hidden';
    $('#targetInfo').style.visibility = 'hidden';
    player.yaw = Math.atan2(-(VAN_IN.x - player.x), -(VAN_IN.z - player.z));
    player.pitch = 0.08;
    const items = [];
    ['hammer', 'pliers', 'cutter', 'handTrowel', 'float'].forEach((id) => {
      const t = gs.tools[id];
      if (t && t.in === 'ground') items.push({ id, obj: lying[id], machine: false });
    });
    if (tripod.visible || gs.carrying === 'laser') { gs.carrying = null; items.push({ id: 'laser', obj: tripod, machine: false }); }
    ['trowelSmall', 'trowelBig'].forEach((id) => {
      const t = gs.tools[id];
      if (t && t.in === 'ground') items.push({ id, obj: machines[id].group, machine: true });
    });
    const t0 = 0.9;
    items.forEach((it, k) => { it.at = t0 + k * 0.65; it.dur = it.machine ? 1.25 : 0.85; });
    gs.packing = { t: 0, items, doneAt: (items.length ? items[items.length - 1].at + items[items.length - 1].dur : t0) + 1.2, stage: 0, hand: 0, machine: 0 };
    toast(pick(L.packUp.start), 'warn');
    if (gs.tools.rideOn && gs.tools.rideOn.in === 'ground') setTimeout(() => toast(L.packUp.rideOn), 1800);
  }
  function updatePack(dt) {
    const pk = gs.packing;
    van.rotation.z = Math.sin(toolT * 38) * vanRock * 0.025;
    van.position.y = Math.abs(Math.sin(toolT * 38)) * vanRock * 0.03;
    vanRock *= Math.exp(-dt * 4);
    if (!pk) return;
    pk.t += dt;
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw), rx = Math.cos(player.yaw), rz = -Math.sin(player.yaw);
    for (const it of pk.items) {
      if (it.landed || pk.t < it.at) continue;
      if (!it.from) {
        // off it goes, from your right hand
        it.from = new THREE.Vector3(player.x + fx * 0.5 + rx * 0.3, 1.25, player.z + fz * 0.5 + rz * 0.3);
        it.obj.visible = true;
        if (it.id !== 'laser') gs.tools[it.id].in = 'flying';
        sfx(it.machine ? 'thunk' : 'pickup');
        if (it.machine && !pk.saidMachine) { pk.saidMachine = true; toast(pick(L.packUp.machine), 'warn'); }
        else if (it.id === 'laser') toast(L.packUp.laser);
        else if (!it.machine && !pk.saidHand) { pk.saidHand = true; setTimeout(() => toast(pick(L.packUp.hand)), 500); }
      }
      const u = clamp((pk.t - it.at) / it.dur, 0, 1), h = it.machine ? 1.2 : 1.9;
      it.obj.position.set(lerp(it.from.x, VAN_IN.x, u), lerp(it.from.y, VAN_IN.y, u) + h * 4 * u * (1 - u), lerp(it.from.z, VAN_IN.z, u));
      it.obj.rotation.set(u * (it.machine ? 3 : 9), u * 4, u * (it.machine ? 2 : 6));
      if (u >= 1) {
        it.landed = true;
        it.obj.visible = false;
        it.obj.rotation.set(0, 0, 0);
        if (it.id === 'laser') { gs.laserPacked = true; } else gs.tools[it.id].in = 'van';
        if (it.machine) pk.machine++; else pk.hand++;
        vanRock = it.machine ? 1 : 0.45;
        sfx(it.machine ? 'thud' : 'clank', VAN_IN.x, VAN_IN.z);
        if (it.machine) sfx('clank', VAN_IN.x, VAN_IN.z);
      }
    }
    if (pk.stage === 0 && pk.t >= pk.doneAt) { pk.stage = 1; sfx('door', VAN_IN.x, VAN_IN.z); vanRock = 0.6; vanHole.visible = vanDoorPanel.visible = false; toast(L.packUp.end); }
    if (pk.stage === 1 && pk.t >= pk.doneAt + 0.9) { pk.stage = 2; sfx('engine', VAN_IN.x, VAN_IN.z); }
    if (pk.stage === 2 && pk.t >= pk.doneAt + 2.4) {
      gs.thrown = { hand: pk.hand, machine: pk.machine };
      remember(pk.hand + pk.machine ? `You threw ${pk.hand + pk.machine} tools in the van and drove off without looking back.` : 'You drove off without looking back.');
      gs.packing = null;
      $('#buttons').style.visibility = '';
      $('#targetInfo').style.visibility = '';
      endDay();
    }
  }

  // ------------------------------------------------------------------ troweling
  function passCoverage(key) { return gs.cells.filter((c) => c[key]).length / gs.cells.length; }
  function completePass(kind) {
    const H = gs.H;
    let verdict, good;
    if (kind === 'pan') {
      good = H >= 25 && H <= 65;
      verdict = H < 25 ? 'Too early: the pans dug in and made waves.' : H <= 65 ? `In the window. ±${rms().toFixed(1)} mm now.` : 'Late pan pass: skated over the top. Better than nothing.';
      gs.cells.forEach((c) => { c.pan++; c.covP = false; });
      gs.panPasses.push({ H, good });
    } else {
      good = H >= 55 && H <= 92 && gs.panPasses.length > 0;
      verdict = !gs.panPasses.length ? 'Blades with no pan pass first. It shines, but it isn\'t flat.' : H < 50 ? 'Blades too early: tore the paste.' : H <= 92 ? 'In the window. It\'s starting to shine.' : 'Blades on a slab that\'s already hard. You\'re polishing a stone.';
      gs.cells.forEach((c) => { c.blade++; c.covB = false; });
      gs.bladePasses.push({ H, good });
    }
    cellsDirty = true;
    surfDirty = true;
    sfx(good ? 'chime' : 'buzz');
    toast(`${kind === 'pan' ? 'Pan' : 'Blade'} pass ${kind === 'pan' ? gs.panPasses.length : gs.bladePasses.length} done at ${Math.floor(H)}%. ${verdict}`, good ? 'good' : 'warn');
  }

  // ------------------------------------------------------------------ the end
  function endDay() {
    gs.phase = 'end';
    $('#hud').hidden = true;
    const dev = rms();
    const defects = gs.cells.filter((c) => c.defect).length;
    const late = Math.max(0, gs.arrived - 6 * 60);
    const goodPans = gs.panPasses.filter((p) => p.good).length;
    const goodBlades = gs.bladePasses.filter((p) => p.good).length;
    let score = 1000 - dev * 22 - defects * 18 - (gs.yelled ? 60 : 0) - (gs.gaveUp ? 80 : 0) - (site.edges.length - Math.min(gs.edgesDone, site.edges.length)) * 12 - (gs.wrongLoad ? L.wrong[gs.wrongLoad].cost : 0) - late * 1.5 - gs.waste * 35 - gs.truckWaitPaid * 0.5 - gs.stats.falls * 10 - gs.edgeNotes.length * 12
      + Math.min(goodPans, 3) * 40 + Math.min(goodBlades, 3) * 40 + gs.stats.hell * 5;
    score = Math.round(clamp(score, 0, 1200));
    const rank = score >= 950 ? 'Slab wizard' : score >= 800 ? 'Proper concrete person' : score >= 620 ? 'Adequate slab operator' : score >= 420 ? 'Footprint curator' : 'The dog\'s favourite';
    const best = Number(store('pourday.best') || 0);
    if (score > best) store('pourday.best', String(score));
    $('#eRank').textContent = rank;
    $('#eScore').textContent = `${score} points` + (score > best ? ' · new best' : best ? ` · best ${best}` : '');
    const rows = [
      ['Arrived', clock(gs.arrived) + (late > 15 ? ` (${dur(late)} late)` : '')],
      ['Poured', clock(gs.pourEnd)],
      ['Home', clockDay(gs.t)],
      ['Shift', dur(gs.t - gs.arrived)],
      ['Weather', `${day.baseTemp.toFixed(0)} °C · ${day.rh}% RH · wind ${day.wind} m/s`],
      ['Slab', `${day.area} m² · ${day.thick} mm · ${volumeNeeded().toFixed(1)} m³`],
      ['Flatness', `±${dev.toFixed(1)} mm`],
      ['Pan / blade passes', `${gs.panPasses.length} / ${gs.bladePasses.length} (${goodPans + goodBlades} in the window)`],
      ['Marks left in it', String(defects)],
      ['Concrete wasted', `${gs.waste.toFixed(1)} m³`],
      ['Truck waiting time', `${Math.round(gs.truckWaitPaid)} €`],
      ['People told to go to hell', String(gs.stats.hell)],
      ['Concrete', gs.wrongLoad ? L.wrong[gs.wrongLoad].row : 'as ordered'],
      ['Phone call with the manager', gs.yelled ? `yes, about ${gs.yelled} marks. Loud.` : 'none, thank God'],
      ['Times on your butt', String(gs.stats.falls)],
    ];
    $('#eStats').innerHTML = rows.map(([k, v]) => `<span>${k}</span><b>${v}</b>`).join('');
    paySlip(late, goodBlades);
    const hour = (gs.t % 1440) / 60;
    const home = gs.t >= 1440 ? `You get home at ${clock(gs.t)}. The cat has moved into your side of the bed.`
      : hour >= 21 ? `Home at ${clock(gs.t)}. Dinner is cold, like your trowel.`
      : hour >= 18 ? `Home at ${clock(gs.t)}. Just in time to fall asleep during the news.`
      : `Home at ${clock(gs.t)}. Before dinner. Suspicious. The neighbours think you've been fired.`;
    const story = gs.story.slice(-7).concat([home]);
    $('#eStory').innerHTML = story.map((s) => `<li>${s.replace(/</g, '&lt;')}</li>`).join('');
    $('#end').hidden = false;
  }

  /** What the day was worth, after the company has had its say. It can go below zero. */
  function paySlip(late, goodBlades) {
    const rep = slabReport(), P2 = L.pay, lines = [];
    const markN = gs.cells.reduce((n, c) => n + c.marks.filter((m) => m.kind !== 'glyph').length, 0);
    const glyphs = gs.cells.reduce((n, c) => n + c.marks.filter((m) => m.kind === 'glyph').length, 0);
    const v = { d: Math.abs(rep.off).toFixed(0), t: day.thick, r: rep.sd.toFixed(1), m: Math.round(rep.slowBy), h: dur(gs.pourMins), n: 0, w: gs.waste.toFixed(1) };
    const cut = (key, eur, extra) => { if (eur >= 1) lines.push([fillIn(pick(P2[key]), Object.assign({}, v, extra || {})), -Math.round(eur)]); };
    if (rep.off < -4) cut('thin', Math.min(160, -rep.off * 8));
    if (rep.off > 5) cut('thick', Math.min(200, (day.area * rep.off / 1000) * 120 + 10));
    if (rep.sd > 5) cut('bumpy', Math.min(150, (rep.sd - 5) * 15 + 10));
    if (rep.slowBy > 10) cut('slow', Math.min(150, rep.slowBy * 1.5));
    if (markN) cut('marks', Math.min(160, markN * 4), { n: markN });
    if (gs.waste > 0.05) cut('waste', gs.waste * 90);
    if (gs.truckWaitPaid >= 1) cut('wait', Math.min(150, gs.truckWaitPaid));
    if (late > 15) cut('late', late, { m: Math.round(late) });
    if (gs.wrongLoad) cut('wrong', L.wrong[gs.wrongLoad].cost / 2);
    if (gs.yelled) cut('manager', 50);
    if (gs.stats.falls) cut('falls', gs.stats.falls * 10, { n: gs.stats.falls });
    if (gs.edgeNotes.length) cut('edges', gs.edgeNotes.length * 15, { n: gs.edgeNotes.length });
    if (glyphs) cut('ufo', 25 + glyphs * 5);
    if (!gs.panPasses.length) cut('noPan', 60);
    if (!gs.bladePasses.length) cut('noBlade', 50);
    if (gs.edgesDone < site.edges.length) cut('roughEdges', (site.edges.length - gs.edgesDone) * 10, { n: site.edges.length - gs.edgesDone });
    if (gs.thrown && gs.thrown.hand + gs.thrown.machine) cut('thrown', gs.thrown.hand * 5 + gs.thrown.machine * 35, { n: gs.thrown.hand + gs.thrown.machine });
    if (gs.stats.hell) lines.push([fillIn(pick(P2.hell), { n: gs.stats.hell }), 0]);
    if (goodBlades >= 2 && rep.sd < 3) lines.push([pick(P2.shine), 40]);
    const base = 220, net = base + lines.reduce((s, l) => s + l[1], 0);
    const owe = P2.verdictBad.filter((v) => /owe us/.test(v)), bad = P2.verdictBad.filter((v) => !/owe us/.test(v));
    const verdict = pick(net < 0 ? owe : net >= 180 ? P2.verdictGood : net <= 60 ? bad : P2.verdictMeh);
    const eur = (x) => (x < 0 ? '−' : x > 0 ? '+' : '') + '€' + Math.abs(x);
    const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;');
    const stampText = net < 0 ? 'You owe us' : net < base * 0.6 ? 'Docked' : '';
    $('#ePay').innerHTML = `<h4><span>Pay slip</span><span>${clock(gs.arrived)}–${clock(gs.t)}</span></h4>`
      + `<div class="row"><span>${esc(P2.base)}</span><b>€${base}</b></div>`
      + lines.map(([t, x]) => `<div class="row"><span>${esc(t)}</span><b class="${x < 0 ? 'neg' : x > 0 ? 'pos' : 'zero'}">${x ? eur(x) : 'no charge'}</b></div>`).join('')
      + `<div class="net"><span>Take home</span><b class="${net < 0 ? 'neg' : ''}">${net < 0 ? '−' : ''}€${Math.abs(net)}</b></div>`
      + `<div class="verdict">${esc(verdict)}</div>`
      + (stampText ? `<div class="stampx">${stampText}</div>` : '');
    setTimeout(() => say(verdict, 'manager'), 900);
    return net;
  }

  // ------------------------------------------------------------------ picking tools up, putting them down
  let nearTool = null, nearToolD = 9, wantTool = null;
  function held() { return gs.tool === 'hands' ? null : gs.tool; }
  function isMachine(id) { return !!(id && TOOLS[id] && TOOLS[id].machine); }
  function unloadTools() {
    TOOL_IDS.forEach((id) => {
      if (id === 'hose' || (id === 'rideOn' && day.area <= 50)) return;
      const [x, z, yaw] = TOOL_HOME[id];
      gs.tools[id] = { in: 'ground', x, z, yaw };
    });
    tarp.visible = true;
  }
  /** Where you take hold of a tool: a hand tool where it lies, a machine by its handle, the ride-on by its seat. */
  function gripOf(id) {
    const t = gs.tools[id];
    if (isMachine(id) && !TOOLS[id].ride) return P(t.x + Math.sin(t.yaw) * machines[id].grip, t.z + Math.cos(t.yaw) * machines[id].grip);
    return P(t.x, t.z);
  }
  function putDown(quiet) {
    const id = held();
    if (!id) return;
    const t = gs.tools[id];
    t.in = 'ground';
    if (TOOLS[id].ride) {
      // off to the left of the seat
      const [nx, nz] = collide(player.x - Math.cos(player.yaw) * 1.5, player.z + Math.sin(player.yaw) * 1.5);
      player.x = nx; player.z = nz;
    } else if (!isMachine(id)) {
      t.x = player.x - Math.sin(player.yaw) * 0.7;
      t.z = player.z - Math.cos(player.yaw) * 0.7;
      t.yaw = player.yaw;
    }
    gs.tool = 'hands';
    sfx(isMachine(id) ? 'thunk' : 'putdown');
    if (!quiet) toastOnce('put' + id, `${TOOLS[id].name} down. It'll be here when you come back for it.`, '', 60000);
  }
  function pickUp(id) {
    if (held()) putDown(true);
    const t = gs.tools[id];
    t.in = 'hand';
    gs.tool = id;
    if (TOOLS[id].ride) {
      player.x = t.x + Math.sin(t.yaw) * 0.3;
      player.z = t.z + Math.cos(t.yaw) * 0.3;
      player.yaw = t.yaw;
      sfx('pullstart');
    } else if (isMachine(id)) {
      player.yaw = Math.atan2(-(t.x - player.x), -(t.z - player.z));
      sfx('pullstart');
    } else sfx('pickup');
    const fit = isMachine(id) ? ` ${gs.fit[id] === 'pans' ? 'Pans' : 'Blades'} on it.` : '';
    toastOnce('take' + id, `${TOOLS[id].name} in hand.${fit}`, '', 45000);
  }
  /** Pans off, blades on, or back: a few minutes with a spanner, standing still. */
  function swapFit() {
    const id = held();
    if (!isMachine(id) || gs.fitting) return;
    if (input.action) { toast('Engine off first. Fingers are not a spare part.', 'warn'); return; }
    const to = gs.fit[id] === 'pans' ? 'blades' : 'pans';
    gs.fitting = { id, to, until: performance.now() + 3200, label: `Fitting the ${to}…`, next: 0 };
    sfx('clank');
  }
  function laserUsable() { return gs.prep.laser && !gs.pourDone; }
  function meshDone() { return site.ties.every((t) => t.done) && site.cuts.every((t) => t.done); }
  /** The discs of the machine in hand, as circles on the ground. */
  function discs() {
    const id = held();
    if (!isMachine(id)) return [];
    const m = machines[id], t = gs.tools[id];
    if (!m.twin) return [{ x: t.x, z: t.z, r: m.R }];
    const rx = Math.cos(t.yaw), rz = -Math.sin(t.yaw);
    return [{ x: t.x - rx * 0.52, z: t.z - rz * 0.52, r: m.R }, { x: t.x + rx * 0.52, z: t.z + rz * 0.52, r: m.R }];
  }
  /** How far a point is from the nearest ground that is not slab: a board, a cut-out corner. */
  function edgeClearance(x, z) {
    let best = 9;
    const i0 = Math.floor(x - SLAB.x0), j0 = Math.floor(z - SLAB.z0);
    for (let j = j0 - 2; j <= j0 + 2; j++) for (let i = i0 - 2; i <= i0 + 2; i++) {
      if (isOn(i, j)) continue;
      const dx = Math.max(gx(i) - x, 0, x - gx(i + 1)), dz = Math.max(gz(j) - z, 0, z - gz(j + 1));
      best = Math.min(best, Math.hypot(dx, dz));
    }
    return best;
  }
  function pipeClearance(x, z) { return site.pens.reduce((m, p) => Math.min(m, hyp(x, z, p.x, p.z) - 0.08), 9); }
  /** Whether a disc fits: never through a pipe, and while it runs on the slab, not through a board. */
  function discFits(x, z, r, running) {
    if (pipeClearance(x, z) < r + 0.03) return false;
    if (running && onSlab(x, z) && edgeClearance(x, z) < r + 0.01) return false;
    return true;
  }

  // ------------------------------------------------------------------ what the big button does
  let target = null;          // the cell under the crosshair, in reach
  let nearMarker = null, nearMarkerD = 9;
  let holdT = 0;
  function holdOf(m) { return typeof m.hold === 'function' ? m.hold() : m.hold; }
  /** Whether what is in your hands will do for a job. */
  function fitsJob(need) { return !need || need === 'hands' || [].concat(need).includes(gs.tool); }
  function context() {
    wantTool = null;
    if (player.fall > 0) return null;
    if (gs.fitting) return { kind: 'none', label: gs.fitting.label };
    // a tool at your feet wins over a job a step further off, so the tarp is not a lottery
    // a job the tool in hand won't do doesn't stop that tool doing its own work; it only says
    // what it needs when there is nothing else to do here
    let needs = null;
    if (nearMarker) {
      if (fitsJob(nearMarker.tool)) return { kind: 'marker', label: nearMarker.label, hold: holdOf(nearMarker) };
      wantTool = [].concat(nearMarker.tool)[0];
      needs = { kind: 'none', label: `Needs ${TOOLS[wantTool].the}` };
    }
    const own = toolContext();
    return own.kind === 'none' && needs ? needs : own;
  }
  function toolContext() {
    const w = walkerInSight();
    if (w) return { kind: 'shout', label: w.kind === 'driver' ? 'Talk' : w.kind === 'dog' ? (gs.sausage ? 'Throw the sausage' : 'Shoo!') : 'Oi! Off the slab!', w };
    const t = gs.tool;
    if (gs.phase === 'pour') {
      if (t === 'hose') {
        if (gs.blocked >= 0) return { kind: 'none', label: 'Line blocked' };
        if (!gs.truck || gs.truck.waiting) return { kind: 'none', label: 'No concrete' };
        if (target) return { kind: 'pour', label: 'Hold: pour' };
        return { kind: 'none', label: 'Aim at the slab' };
      }
      if (t === 'float') {
        if (target && target.fill > 5) return { kind: 'level', label: 'Hold: float' };
        return { kind: 'none', label: 'Aim at concrete' };
      }
      if (t === 'hands' && gs.tools.hose && gs.tools.hose.in === 'ground') return { kind: 'none', label: 'Get the hose' };
    }
    if (gs.phase === 'cure') {
      if (t === 'float' && target) return { kind: 'repair', label: target.marks.length ? 'Hold: float out marks' : 'Hold: float' };
      if (t === 'handTrowel' && target) return { kind: 'repair', label: target.marks.length ? 'Hold: trowel out marks' : 'Hold: hand trowel' };
      // the machine runs out ahead of you, so it is where its discs are that counts
      if (isMachine(t)) {
        const on = discs().some((d) => onSlab(d.x, d.z));
        if (on) return { kind: 'trowel', label: `Hold: ${gs.fit[t] === 'pans' ? 'pan' : 'blade'} pass` };
        return { kind: 'none', label: TOOLS[t].ride ? 'Drive onto the slab' : 'Steer it onto the slab' };
      }
      if (t === 'hands' && target) return { kind: 'thumb', label: 'Thumb test' };
    }
    return { kind: 'none', label: '—' };
  }

  function doAction(ctx, dt) {
    if (!ctx) return;
    if (ctx.kind === 'marker') {
      const m = nearMarker;
      // a job for two hands: whatever was in them goes down first
      if (m.tool === 'hands' && held()) { const was = TOOLS[gs.tool].name; putDown(true); toastOnce('drop', `You put the ${was.toLowerCase()} down.`, '', 20000); }
      holdT += dt;
      if (holdT >= holdOf(m)) {
        holdT = 0;
        const ok = m.done();
        if (ok !== false && !m.active()) m.group.visible = false;
      }
      return;
    }
    if (ctx.kind === 'shout') { if (input.actionTapped) shoutAt(ctx.w); return; }
    if (ctx.kind === 'pour') pourTick(dt);
    else if (ctx.kind === 'level') levelTick(target, dt);
    else if (ctx.kind === 'repair') repairTick(target, dt);
    else if (ctx.kind === 'trowel') trowelTick(dt);
    else if (ctx.kind === 'thumb' && input.actionTapped) thumb(target);
  }

  let pourSeconds = 0;
  let paintT = 0;
  function pourTick(dt) {
    if (!target || !gs.truck || gs.truck.left <= 0) return;
    const speed = gs.mixState === 'stiff' ? 90 : gs.mixState === 'soup' ? 125 : 110;
    const add = speed * dt;
    const nb = neighbours(target);
    const spread = gs.mixState === 'soup' ? 0.4 : 0.25;
    target.fill += add * (1 - spread);
    nb.forEach((n) => { n.fill += (add * spread) / nb.length; });
    const m3 = add / 1000;
    gs.truck.left -= m3;
    gs.pouredM3 += m3;
    cellsDirty = true;
    pourSeconds += dt;
    paintT -= dt;
    if (paintT <= 0) {
      paintT = 0.07;
      paintPour(target._hx + rnd(-0.2, 0.2), target._hz + rnd(-0.2, 0.2));
      if (gs.wrongLoad === 'fibre') paintFibres(target._hx, target._hz);
    }
    const progress = filledShare();
    // what goes wrong while pouring
    if (gs.mixState === 'stiff' && chance(0.025 * dt)) {
      gs.blocked = irnd(1, 5);
      gs.stats.blockages++;
      toast(pick(L.blocked), 'warn');
      blockMarker();
    }
    const weakAt = site.forms.findIndex((f) => f.run === site.weak);
    if (site.weak && !gs.blowoutDone && !gs.blowout && progress > 0.35 && weakAt >= 0 && !gs.prep.form[weakAt]) startBlowout();
    if (day.batteryDies && gs.prep.laser && !gs.batteryDone && progress > 0.45) {
      gs.batteryDone = true;
      gs.laserBattery = false;
      gs.laserOn = false;
      toast(L.battery, 'warn');
    }
    if (gs.truck.left <= 0) truckEmpty();
  }
  function blockMarker() {
    const k = gs.blocked;
    const a = k === 0 ? POS.pumpOut : PIPE_ROUTE[k - 1], b = PIPE_ROUTE[k];
    const at2 = day.boom ? P(pump.position.x + 1.5, pump.position.z + 3.2) : P((a.x + b.x) / 2, (a.z + b.z) / 2);
    const m = addMarker('block', at2, day.boom ? 'Hit the boom pipe!' : 'Hit the pipe!', 2.4, () => gs.blocked >= 0, () => {
      gs.blocked = -1;
      sfx('splash');
      toast(pick(L.unblocked), 'good');
      removeMarker(m);
    }, { w: 2.2, tool: 'hammer' });
  }
  function startBlowout() {
    const r = site.weak;
    const side = r.dir === 'x' ? (r.oz < 0 ? 'north' : 'south') : (r.ox < 0 ? 'west' : 'east');
    const s = { name: `${side} side`, p: P(r.mid.x + r.ox * 0.9, r.mid.z + r.oz * 0.9), cells: new Set(r.cells.map((c) => c.j * NX + c.i)) };
    gs.blowout = s;
    toast(L.blowout.replace('{side}', s.name), 'warn');
    sfx('splash', s.p.x, s.p.z);
    remember(`The formwork burst on the ${s.name}.`);
    const m = addMarker('blowout', s.p, 'Fix the formwork!', 2.8, () => !!gs.blowout, () => {
      gs.blowout = null;
      gs.blowoutDone = true;
      toast('Stakes, a board and a lot of swearing. It holds.', 'good');
      removeMarker(m);
    }, { w: 2.6, tool: 'hammer' });
  }
  function levelTick(c, dt) {
    const rate = 55 * dt * (gs.mixState === 'soup' ? 1.5 : gs.mixState === 'stiff' ? 0.65 : 1);
    const diff = c.fill - day.thick;
    const nb = neighbours(c);
    if (diff > 0.3) {
      const low = nb.reduce((a, b) => (b.fill < a.fill ? b : a));
      if (low.fill < c.fill) { const amt = Math.min(rate, diff, (c.fill - low.fill) / 2); c.fill -= amt; low.fill += amt; }
    } else if (diff < -0.3) {
      const high = nb.reduce((a, b) => (b.fill > a.fill ? b : a));
      if (high.fill > c.fill) { const amt = Math.min(rate, -diff, (high.fill - c.fill) / 2); c.fill += amt; high.fill -= amt; }
    }
    cellsDirty = true;
    paintT -= dt;
    if (paintT <= 0) { paintT = 0.06; paintFloat(floatTool.position.x, floatTool.position.z, player.yaw); }
  }
  /** The hand float on a slab that is going off: wears prints out under 50%, after that it only polishes. */
  /**
   * Working a spot by hand. The float wears prints out while it is under 50%; the hand trowel is
   * slower but keeps going to 70%, and gets where no machine can.
   */
  function repairTick(c, dt) {
    const hand = gs.tool === 'handTrowel';
    const limit = hand ? 70 : 50;
    paintT -= dt;
    if (gs.H < limit && paintT <= 0) {
      paintT = 0.08;
      if (hand) paintBlade(target._hx, target._hz, 0.16); else paintFloat(floatTool.position.x, floatTool.position.z, player.yaw);
    }
    if (!c.marks.length || c.defect) return;
    if (gs.H >= limit) {
      if (input.actionTapped) toastOnce('toohard' + gs.tool, hand ? 'Too hard even for the hand trowel. The machine might, until about 80%.' : 'Too hard for the float now. The hand trowel or the machine can still do it, until 70–80%.', 'warn', 30000);
      return;
    }
    if (wearMarks(c, (hand ? 0.7 : 1.1) * dt)) toastOnce('floatout', 'Floated out. Nobody will ever know. Except you. Forever.', 'good', 40000);
  }

  // the power trowels
  const mpos = { x: 0, z: 0, on: false };   // the middle of the machine in hand, set every frame
  /** 0 when the concrete carries the machine, up to 1 when it is still soft enough to dig into. */
  function digFactor() {
    const H = gs.H, heavy = gs.tool === 'rideOn' ? 5 : 0;
    return fitted() === 'blades' ? clamp((50 - H) / 20, 0, 1) : clamp((25 + heavy - H) / 15, 0, 1);
  }
  let dugWarn = 0;
  function trowelTick(dt) {
    const id = gs.tool, pans = fitted() === 'pans', H = gs.H;
    if (H < 8) { if (input.actionTapped) toast(pans ? L.tooSoftMachine : L.tooSoftBlades, 'warn'); return; }
    if (input.actionTapped && pans && gs.panPasses.length >= 3) toastOnce('pans3', 'Three pan passes is plenty. Now you\'re just polishing the pans.', '', 60000);
    if (input.actionTapped && !pans && gs.bladePasses.length >= 3) toastOnce('blades3', 'Three blade passes. It shines like a bowling alley. You can stop.', '', 60000);
    const key = passKey();
    const dig = digFactor();
    // how much the surface still gives: everything at 50%, nothing at 80%. The pan wipes prints
    // 60% faster than the blades: one steady pass takes a fresh boot print out with the pan.
    const give = clamp((80 - H) / 30, 0, 1);
    const wear = (pans ? 1.6 : 1.0) * give * dt;
    const ds = discs();
    let covered = false;
    for (const disc of ds) {
      const R = disc.r;
      for (const c of gs.cells) {
        const ccx = SLAB.x0 + c.i + 0.5, ccz = SLAB.z0 + c.j + 0.5;
        const d = hyp(ccx, ccz, disc.x, disc.z);
        if (d > 1.4) continue;
        // marks: worn down where the disc actually is, harder the closer to its middle
        if (c.marks.length && !c.defect && wear > 0) {
          let gone = false;
          for (const m of c.marks) {
            const dm = hyp(m.x, m.z, disc.x, disc.z);
            if (dm < R + 0.06) { m.depth -= wear * (1.25 - dm / (R / 0.46)); gone = true; }
          }
          if (gone) {
            const before = c.marks.length;
            c.marks = c.marks.filter((m) => m.depth > 0.05);
            if (before && !c.marks.length) gs.stats.repaired++;
            surfDirty = true;
          }
        }
        const reach = R + 0.5;
        if (d > reach) continue;
        const w = 1 - d / reach;
        if (d < R + 0.35 && !c[key]) { c[key] = true; covered = true; surfDirty = true; }
        if (dig > 0) {
          // too soft: the disc pushes the paste out from under itself and it heaps up round the rim
          const push = 26 * dig * dt * (pans ? 1 : 0.5) * (id === 'rideOn' ? 1.4 : 1);
          if (d < R + 0.04) c.fill -= push * w + rnd(0, 6) * dig * dt;
          else c.fill += push * 0.5 * w + rnd(-4, 6) * dig * dt;
          c.fill = Math.max(10, c.fill);
        } else if (give > 0) {
          // the pans flatten, towards the neighbours and the laser height; the blades only a little
          const nb = neighbours(c);
          const avg = nb.length ? nb.reduce((sum, n) => sum + n.fill, 0) / nb.length : c.fill;
          const k = Math.min(1, (pans ? 0.6 : 0.15) * give * w * dt * (R / 0.46));
          c.fill += (lerp(avg, day.thick, 0.3) - c.fill) * k;
        }
      }
    }
    if (dig > 0) {
      gs.stats.dug += dt * dig;
      if (performance.now() > dugWarn) {
        dugWarn = performance.now() + 20000;
        toast(pans ? 'The pans are digging in. Waves everywhere. It\'s too soft — wait for 25%.' : 'The blades are tearing the paste. Too early for blades.', 'warn');
        remember(pans ? 'The pans went on too early and dug in.' : 'The blades went on too early and tore the surface.');
      }
    }
    cellsDirty = true;
    paintT -= dt;
    if (paintT <= 0) {
      paintT = 0.05;
      for (const disc of ds) {
        if (!onSlab(disc.x, disc.z)) continue;
        if (dig > 0.15 && chance(0.25 + dig * 0.5)) paintGouge(disc.x + rnd(-0.3, 0.3) * disc.r, disc.z + rnd(-0.3, 0.3) * disc.r, rnd(0, 6.3));
        else if (pans) paintPan(disc.x, disc.z, disc.r);
        else paintBlade(disc.x, disc.z, disc.r);
      }
    }
    if (covered && passCoverage(key) >= 0.9) {
      if (pans && gs.panPasses.length >= 3) { gs.cells.forEach((c) => { c.covP = false; }); return; }
      if (!pans && gs.bladePasses.length >= 3) { gs.cells.forEach((c) => { c.covB = false; }); return; }
      completePass(pans ? 'pan' : 'blade');
    }
  }
  function thumb(c) {
    if (!gs.poured) { toast('It\'s still a building site, not a slab. Nothing to test.'); return; }
    const line = L.thumb.find(([h]) => gs.H < h)[1];
    sfx(gs.H < 60 ? 'soft' : 'hard');
    toast(`${Math.floor(gs.H)}% · ${line}`);
    if (gs.H < 30) stamp('thumb', SLAB.x0 + c.i + 0.5 + rnd(-0.3, 0.3), SLAB.z0 + c.j + 0.5 + rnd(-0.3, 0.3), 0);
  }

  // ------------------------------------------------------------------ controls
  const touchLayer = $('#touch');
  const joy = $('#joy'), joyKnob = $('#joyKnob');
  let joyId = null, lookId = null, joyCx = 0, joyCy = 0, lookX = 0, lookY = 0;
  touchLayer.addEventListener('pointerdown', (e) => {
    if (gs.phase === 'title' || gs.phase === 'end' || modalOpen) return;
    try { touchLayer.setPointerCapture(e.pointerId); } catch (err) { /* not every pointer can be captured */ }
    if (e.pointerType !== 'mouse' && e.clientX < window.innerWidth * 0.45 && joyId === null) {
      joyId = e.pointerId; joyCx = e.clientX; joyCy = e.clientY;
      joy.style.left = joyCx + 'px'; joy.style.top = joyCy + 'px'; joy.hidden = false;
      joyKnob.style.transform = 'translate(0,0)';
    } else if (lookId === null) {
      lookId = e.pointerId; lookX = e.clientX; lookY = e.clientY;
    }
  });
  touchLayer.addEventListener('pointermove', (e) => {
    if (e.pointerId === joyId) {
      let dx = e.clientX - joyCx, dy = e.clientY - joyCy;
      const len = Math.hypot(dx, dy), max = 52;
      if (len > max) { dx = (dx / len) * max; dy = (dy / len) * max; }
      input.jx = dx / max; input.jy = dy / max;
      joyKnob.style.transform = `translate(${dx}px, ${dy}px)`;
    } else if (e.pointerId === lookId) {
      const sens = e.pointerType === 'mouse' ? 0.0045 : 0.0058;
      player.yaw -= (e.clientX - lookX) * sens;
      player.pitch = clamp(player.pitch - (e.clientY - lookY) * sens, -1.35, 1.1);
      lookX = e.clientX; lookY = e.clientY;
    }
  });
  const release = (e) => {
    if (e.pointerId === joyId) { joyId = null; input.jx = 0; input.jy = 0; joy.hidden = true; }
    if (e.pointerId === lookId) lookId = null;
  };
  touchLayer.addEventListener('pointerup', release);
  touchLayer.addEventListener('pointercancel', release);

  window.addEventListener('keydown', (e) => {
    input.keys[e.code] = true;
    if (modalOpen || gs.phase === 'title' || gs.phase === 'end') return;
    if (e.code === 'KeyE' || e.code === 'Space') { if (!input.action) input.actionTapped = true; input.action = true; e.preventDefault(); }
    if (e.code === 'KeyQ') toolButton();
    if (e.code === 'KeyL') altButton();
    if (e.code === 'KeyT') waitMenu();
    if (e.code === 'KeyC') coffee();
    if (e.code === 'Escape' || e.code === 'KeyP') pauseMenu();
  });
  window.addEventListener('keyup', (e) => {
    input.keys[e.code] = false;
    if (e.code === 'KeyE' || e.code === 'Space') input.action = false;
  });

  // The work button also looks: hold it and slide the thumb, and the view (and the machine, the
  // float, the hose) follows — steering a trowel means holding the throttle and turning at once.
  const btnAction = $('#btnAction');
  let actId = null, actX = 0, actY = 0;
  btnAction.addEventListener('pointerdown', (e) => {
    e.preventDefault();
    try { btnAction.setPointerCapture(e.pointerId); } catch (err) { /* not every pointer can be captured */ }
    actId = e.pointerId; actX = e.clientX; actY = e.clientY;
    input.action = true; input.actionTapped = true; btnAction.classList.add('held');
    sfx('click');
  });
  btnAction.addEventListener('pointermove', (e) => {
    if (e.pointerId !== actId) return;
    const sens = e.pointerType === 'mouse' ? 0.0045 : 0.0058;
    player.yaw -= (e.clientX - actX) * sens;
    player.pitch = clamp(player.pitch - (e.clientY - actY) * sens, -1.35, 1.1);
    actX = e.clientX; actY = e.clientY;
  });
  ['pointerup', 'pointercancel', 'lostpointercapture'].forEach((ev) => btnAction.addEventListener(ev, (e) => {
    if (actId !== null && e.pointerId !== actId) return;
    actId = null;
    input.action = false; btnAction.classList.remove('held');
  }));
  /**
   * The tool button picks up and puts down. Picking up is only ever this button, never the work
   * button: holding the work button to look round the tarp used to pick up whatever it passed over.
   */
  function toolButton() {
    if (gs.carrying) { toastOnce('carry', 'Both hands are on what you\'re carrying.', '', 20000); return; }
    if (nearTool) pickUp(nearTool);
    else if (held()) putDown();
    else toastOnce('hands', 'Your hands are empty. Look at a tool — on the tarp by the van, or wherever you left it — and press Pick up.', '', 20000);
  }
  $('#btnTool').addEventListener('click', () => toolButton());
  $('#btnLaser').addEventListener('click', () => altButton());
  $('#btnWait').addEventListener('click', () => waitMenu());
  $('#btnCoffee').addEventListener('click', () => coffee());
  $('#btnFinish').addEventListener('click', () => {
    modal({
      who: 'Finish the pour?', title: `${Math.round(filledShare() * 100)}% filled, ±${rms().toFixed(1)} mm.`,
      text: 'Once it\'s finished there\'s no more floating to the laser — it goes to the pans from here.',
      choices: [{ label: 'Finish the pour', primary: true, fn: () => finishPour() }, { label: 'Keep floating' }],
    });
  });

  /** The second small button: the machine's pans or blades while you hold one, the laser receiver while the laser is up. */
  function altButton() {
    if (isMachine(held())) swapFit();
    else if (laserUsable()) toggleLaser();
  }
  function toggleLaser() {
    if (!gs.prep.laser) { toast('The laser isn\'t set up. Tripod by the west edge — and the tools out of the van first.', 'warn'); return; }
    if (!gs.laserBattery) { toast('Dead batteries. The spares are in the van.', 'warn'); return; }
    if (gs.pourDone) { toast('The pour is done. It\'s the pans that flatten it now.'); return; }
    gs.laserOn = !gs.laserOn;
    cellsDirty = true;
    sfx('beep');
    toast(gs.laserOn ? 'Laser receiver on: green is on height, red is high, blue is low.' : 'Laser receiver off. Eyeballing it. Bold.');
  }
  function coffee() {
    if (gs.cups <= 0) { toast(L.noCoffee, 'warn'); return; }
    gs.cups--;
    gs.stats.coffee++;
    gs.energy = clamp(gs.energy + 15, 0, 100);
    simulate(2, awayNow());
    cupT = 1.6;
    sfx('slurp');
    toast(`${pick(L.coffee)} (${gs.cups} left)`);
  }
  function showWait() {
    const b = $('#waitBadge');
    if (!gs.waitMode && !gs.fastForward) { b.hidden = true; return; }
    b.hidden = false;
    b.innerHTML = gs.waitMode === 'van' ? 'In the van · time flies<small>Tap Wait to get out</small>'
      : gs.waitMode === 'guard' ? 'Guarding the slab · time flies<small>Move or tap Wait to stop</small>'
      : 'Waiting · time flies<small>Move to stop</small>';
  }
  function waitMenu() {
    if (gs.waitMode || gs.fastForward) {
      if (gs.waitMode === 'van') { player.x = POS.vanDoor.x; player.z = POS.vanDoor.z; toast(pick(L.vanNap)); }
      gs.waitMode = null; gs.fastForward = null; showWait();
      return;
    }
    const choices = [];
    if ((gs.phase === 'prep' || gs.phase === 'pipes') && !gs.pumpHere) choices.push({ label: `Wait for the pump (due ${clock(gs.pumpAt)})`, primary: true, fn: () => { gs.fastForward = 'pump'; showWait(); } });
    if ((gs.phase === 'pipes' || gs.phase === 'pour') && gs.pipes === 6 && !gs.truck && gs.nextTruckAt > gs.t) choices.push({ label: `Wait for the truck (due ${clock(gs.nextTruckAt)})`, primary: true, fn: () => { gs.fastForward = 'truck'; showWait(); } });
    if (gs.phase === 'cure' || gs.phase === 'wash') {
      if (hyp(player.x, player.z, site.mid.x, site.mid.z) < 16) choices.push({ label: 'Stand guard by the slab', primary: true, fn: () => { gs.waitMode = 'guard'; showWait(); } });
      choices.push({ label: 'Nap in the van (fastest, but nobody guards the slab)', fn: () => { gs.waitMode = 'van'; showWait(); } });
    }
    if (!choices.length) { toast('Nothing to wait for. There is always something to do. That\'s the job.'); return; }
    choices.push({ label: 'Never mind' });
    modal({ who: 'Wait', title: 'Let time do its thing.', text: gs.poured ? `Hardness ${Math.floor(gs.H)}%. Pans in ${dur(etaTo(25))}, blades in ${dur(etaTo(55))}, 95% in ${dur(etaTo(95))}.` : '', choices });
  }

  // ------------------------------------------------------------------ per frame
  const tmpV = new THREE.Vector3(), tmpV2 = new THREE.Vector3();
  let stuckMsg = 0;
  let stepSnd = 0;
  function updatePlayer(dt) {
    let mx = input.jx, my = input.jy;
    if (input.keys.KeyW || input.keys.ArrowUp) my -= 1;
    if (input.keys.KeyS || input.keys.ArrowDown) my += 1;
    if (input.keys.KeyA || input.keys.ArrowLeft) mx -= 1;
    if (input.keys.KeyD || input.keys.ArrowRight) mx += 1;
    const len = Math.hypot(mx, my);
    if (len > 1) { mx /= len; my /= len; }
    const wants = len > 0.08;
    if (wants && (gs.waitMode === 'guard' || gs.fastForward)) { gs.waitMode = null; gs.fastForward = null; showWait(); }
    if (gs.waitMode === 'van') { player.x = POS.van.x + 0.6; player.z = POS.van.z - 0.2; player.moving = false; return; }
    if (gs.packing) { player.moving = false; return; }
    if (player.fall > 0 || performance.now() < gs.stuckUntil || gs.fitting) { player.moving = false; return; }
    const c = cellAt(player.x, player.z);
    const wet = gs.phase === 'pour' && c && c.fill > 20;
    const tool = gs.tool, running = input.action && lastCtxKind === 'trowel';
    let speed = 4.2;
    if (wet) speed = 1.7;
    if (isMachine(tool)) speed = TOOLS[tool].ride ? (running ? 1.8 : 2.6) : (running ? 1.4 : 2.2);
    if (gs.carrying) speed *= 0.7;
    if (gs.energy < 20) speed *= 0.8;
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    const rx = Math.cos(player.yaw), rz = -Math.sin(player.yaw);
    const vx = (fx * -my + rx * mx) * speed, vz = (fz * -my + rz * mx) * speed;
    let [nx, nz] = collide(player.x + vx * dt, player.z + vz * dt);
    // what you are holding decides where you can go: the hose is only so long, a walk-behind
    // machine is in front of you and does not go through pipes, the ride-on is the size it is
    const from = day.boom ? P(pump.position.x + TURRET.x, pump.position.z) : PIPE_ROUTE[5];
    const reach = day.boom ? BOOM_REACH + 3.5 : site.hoseReach;
    if (tool === 'hose' && hyp(nx, nz, from.x, from.z) > reach && hyp(nx, nz, from.x, from.z) > hyp(player.x, player.z, from.x, from.z)) {
      nx = player.x; nz = player.z;
      toastOnce('hoselen', day.boom ? 'The boom is all the way out. The pump driver shrugs at you from the truck.' : 'That\'s all the hose there is.', '', 30000);
    }
    if (isMachine(tool) && !TOOLS[tool].ride) {
      const tl = gs.tools[tool];
      const dNew = hyp(nx, nz, tl.x, tl.z), dOld = hyp(player.x, player.z, tl.x, tl.z);
      if ((dNew < 1.5 && dNew < dOld) || (dNew > 2.8 && dNew > dOld)) { nx = player.x; nz = player.z; }
    }
    if (tool === 'rideOn') {
      const yaw = player.yaw, cxn = nx + fx * 0.3, czn = nz + fz * 0.3;
      const rxw = Math.cos(yaw), rzw = -Math.sin(yaw);
      const fitsAt = (x, z) => [-0.52, 0.52].every((o) => discFits(x + rxw * o, z + rzw * o, 0.46, running));
      if (!fitsAt(cxn, czn) && fitsAt(player.x + fx * 0.3, player.z + fz * 0.3)) { nx = player.x; nz = player.z; }
    }
    const moved = hyp(nx, nz, player.x, player.z);
    player.x = nx; player.z = nz;
    player.moving = moved > 0.001;
    player.bob += moved * 2.6;
    // walking on it
    const now = cellAt(player.x, player.z);
    stepSnd += moved;
    if (stepSnd > (now && gs.phase === 'pour' && now.fill > 20 ? 0.55 : 0.72)) {
      stepSnd = 0;
      if (!now || (!gs.pourStarted && now.fill < 3)) sfx('gravel');
      else if (gs.phase === 'pour' && now.fill > 20) sfx('wet');
      else if (gs.poured && gs.H < 55) sfx('soft');
      else sfx('hard');
    }
    if (now && moved > 0) {
      player.stepAcc += moved;
      if (gs.phase === 'pour' && gs.pourStarted && now.fill > 30) {
        if (!gs.fellInPour && filledShare() > 0.2 && chance(0.0015)) { gs.fellInPour = true; fall(); }
        else if (chance(0.0025) && performance.now() > stuckMsg) { gs.stuckUntil = performance.now() + 2600; stuckMsg = performance.now() + 60000; toast(pick(L.stuck)); }
      }
      // your own boots print too, for as long as the concrete takes a print — troweled or not.
      // Walking behind a machine the step is flatter and lighter; on the ride-on you're sitting.
      if (gs.poured && gs.tool !== 'rideOn' && player.stepAcc > 0.72) {
        player.stepAcc = 0;
        const side = (Math.floor(player.bob / Math.PI) % 2 ? 0.12 : -0.12);
        const px = player.x + Math.cos(player.yaw) * side, pz = player.z - Math.sin(player.yaw) * side;
        if (stamp('boot', px, pz, player.yaw, true, isMachine(gs.tool) ? 0.6 : 1)) {
          gs.stats.own++;
          toastOnce('own', gs.panPasses.length ? 'Your boots are printing in your fresh trowel work. The next pass can take them — while it\'s still soft enough.' : 'You are leaving footprints in your own slab. The dog is laughing at you.', 'warn', 120000);
        }
      }
      if (machineTool() && input.action && chance(0.006 * digFactor())) fall('The machine digs in, twists, and throws you on your butt. Told you it was early.');
      if (gs.energy < 18 && chance(0.003)) fall('Your legs file for early retirement. Down you go.');
    }
  }

  function updateTarget() {
    camera.getWorldDirection(tmpV);
    const o = camera.getWorldPosition(tmpV2);
    target = null;
    if (tmpV.y < -0.02) {
      const yPlane = gs.pourStarted ? surfY(day.thick) : 0.02;
      const t = (yPlane - o.y) / tmpV.y;
      const x = o.x + tmpV.x * t, z = o.z + tmpV.z * t;
      if (onSlab(x, z) && hyp(x, z, player.x, player.z) < REACH) { target = cellAt(x, z); target._hx = x; target._hz = z; }
    }
    nearMarker = null;
    let best = 2.0;
    for (const m of markers) {
      if (!m.active()) continue;
      let d = hyp(m.x, m.z, player.x, player.z);
      // the edge trowel does a straight edge from where the machine is, not from your boots
      if (m.byMachine && gs.tool === 'trowelSmall' && mpos.on) d = Math.min(d, hyp(m.x, m.z, mpos.x, mpos.z) + 0.6);
      if (d < best) { best = d; nearMarker = m; }
    }
    nearMarkerD = best;
    nearTool = null; nearToolD = 9;
    if (!gs.carrying && player.fall <= 0 && gs.waitMode !== 'van' && !gs.fitting) {
      // the tool you are looking at: at a hand tool where it lies, at a machine's handle
      camera.getWorldDirection(tmpV);
      const o = camera.position;
      let bestA = 9;
      for (const id of TOOL_IDS) {
        const tl = gs.tools[id];
        if (!tl || tl.in !== 'ground') continue;
        const g = gripOf(id);
        const d = hyp(g.x, g.z, player.x, player.z);
        if (d > 2.2) continue;
        const gy = isMachine(id) ? (TOOLS[id].ride ? 0.8 : 0.9) : 0.05;
        const dx = g.x - o.x, dy = gy - o.y, dz = g.z - o.z, d3 = Math.hypot(dx, dy, dz);
        const a = Math.acos(clamp((dx * tmpV.x + dy * tmpV.y + dz * tmpV.z) / d3, -1, 1));
        // roughly in view counts: a tool on the ground is well below where you usually look
        if (a < Math.atan((isMachine(id) ? 0.5 : 0.4) / d3) + 0.24 && a < bestA) { bestA = a; nearTool = id; nearToolD = d; }
      }
    }
  }

  let lastCtxKind = '';
  const btnState = { idle: null, label: null, fill: null, tool: null, alt: null, wait: null };
  const btnTool = $('#btnTool'), btnAlt = $('#btnLaser');
  const actLabel = $('#actLabel'), actFill = $('#actFill');
  function updateAction(dt) {
    if (gs.packing) { input.action = false; return; }
    updateTarget();
    const ctx = context();
    if (!ctx || ctx.kind !== lastCtxKind) holdT = 0;
    lastCtxKind = ctx ? ctx.kind : '';
    if (input.action && ctx && ctx.kind !== 'none') {
      if (gs.waitMode === 'guard') { gs.waitMode = null; showWait(); }
      doAction(ctx, dt);
    } else {
      holdT = 0;
      if (input.actionTapped && ctx && ctx.kind === 'none' && ctx.label !== '—') toastOnce('idle' + ctx.label, ctx.label + '.', '', 12000);
    }
    if (ctx && ctx.kind === 'pour' && input.action && target && gs.truck && gs.truck.left > 0) {
      stream.visible = true;
      const nozzle = new THREE.Vector3(0.2, -0.45, -1.2);
      camera.localToWorld(nozzle);
      stretch(stream, nozzle, new THREE.Vector3(target._hx, surfY(target.fill), target._hz));
    } else stream.visible = false;
    input.actionTapped = false;
    // the end hose, from the last pipe to your hand, or to wherever you left it
    const hose = gs.tools.hose;
    if (hose && (hose.in === 'hand' || hose.in === 'ground')) {
      const end = new THREE.Vector3();
      if (hose.in === 'hand') { end.set(0.36, -0.75, -0.35); camera.localToWorld(end); }
      else end.set(hose.x, groundY(hose.x, hose.z) + 0.05, hose.z);
      const last = PIPE_ROUTE[5];
      endHose.visible = true;
      stretch(endHose, day.boom ? boomTip : new THREE.Vector3(last.x, 0.15, last.z), end);
    } else endHose.visible = false;
    // HUD button
    const idle = !ctx || ctx.kind === 'none';
    const label = ctx ? (ctx.kind === 'marker' ? `Hold: ${ctx.label}` : ctx.label) : '—';
    const fill = ctx && ctx.kind === 'marker' ? `${Math.min(100, (holdT / ctx.hold) * 100)}%` : '0%';
    if (idle !== btnState.idle) { btnAction.classList.toggle('idle', idle); btnState.idle = idle; }
    if (label !== btnState.label) { actLabel.textContent = label; btnState.label = label; }
    if (fill !== btnState.fill) { actFill.style.width = fill; btnState.fill = fill; }
  }

  /** The shortest turn from angle a to angle b. */
  function turnTo(a, b, k) {
    let d = b - a;
    while (d > Math.PI) d -= Math.PI * 2;
    while (d < -Math.PI) d += Math.PI * 2;
    return a + d * k;
  }
  function updateWalkers(dt) {
    updateOdd(dt);
    for (let k = walkers.length - 1; k >= 0; k--) {
      const w = walkers[k], u = w.m.userData, dog = w.kind === 'dog';
      const x0 = w.m.position.x, z0 = w.m.position.z;
      // a person looks at you when you are near; a dog always keeps an eye on you
      if (!dog && u.head) {
        const d = hyp(player.x, player.z, x0, z0);
        const rel = turnTo(0, Math.atan2(player.x - x0, player.z - z0) - w.m.rotation.y, 1);
        u.head.rotation.y = lerp(u.head.rotation.y, d < 12 ? clamp(rel, -1.1, 1.1) : 0, 1 - Math.exp(-dt * 4));
      }
      if (w.pause > 0) {
        w.pause -= dt;
        // standing about: breathing, a phone, a sniff, a sit
        if (dog) {
          u.head.rotation.z = lerp(u.head.rotation.z, w.pose === 'sniff' ? -0.7 : w.pose === 'bark' ? 0.25 + Math.sin(toolT * 18) * 0.12 : 0, 1 - Math.exp(-dt * 6));
          u.body.rotation.z = lerp(u.body.rotation.z, w.pose === 'sit' ? 0.45 : 0, 1 - Math.exp(-dt * 6));
          u.tail.rotation.x = Math.sin(toolT * 14) * 0.7;
          u.legs.forEach((l) => { l.rotation.z *= 0.9; });
        } else {
          u.legL.rotation.x *= 0.85; u.legR.rotation.x *= 0.85;
          u.armL.rotation.x *= 0.85;
          u.armR.rotation.x = lerp(u.armR.rotation.x, w.pose === 'phone' ? -2.5 : 0, 1 - Math.exp(-dt * 6));
          u.body.position.y = Math.sin(toolT * 2) * 0.004;
        }
        if (w.pause <= 0 && w.afterPause) { const f = w.afterPause; w.afterPause = null; f(w); }
        if (w.pause <= 0) w.pose = null;
        continue;
      }
      const a = w.path[w.seg], b = w.path[w.seg + 1];
      if (!b) {
        if (w.onArrive) { const f = w.onArrive; w.onArrive = null; f(w); if (w.pause > 0 || w.path[w.seg + 1]) continue; }
        scene.remove(w.m); walkers.splice(k, 1); if (w.onDone) w.onDone();
        continue;
      }
      const len = hyp(a.x, a.z, b.x, b.z) || 0.001;
      w.acc += (w.speed * dt) / len;
      if (w.acc >= 1) {
        w.acc = 0; w.seg++;
        // at a bend: people sometimes stop to look at their phone; dogs sniff, sit or bark
        if (!dog && chance(0.16) && w.path[w.seg + 1]) { w.pause = rnd(0.8, 2.4); w.pose = pick(['look', 'phone']); }
        if (dog && chance(0.28) && w.path[w.seg + 1]) {
          w.pose = pick(['sniff', 'sniff', 'sit', 'bark']);
          w.pause = rnd(0.7, 1.8);
          if (w.pose === 'bark') sfx(w.cat ? 'meow' : 'bark', x0, z0);
          if (w.pose === 'sit') stamp('paw', x0, z0, w.heading, true);
        }
        continue;
      }
      const x = lerp(a.x, b.x, w.acc), z = lerp(a.z, b.z, w.acc);
      const moved = hyp(x, z, x0, z0);
      const sink = gs.phase === 'pour' && onSlab(x, z) && fillAt(x, z) > 20 ? 0.06 : 0;
      w.m.position.set(x, groundY(x, z) - sink, z);
      w.heading = headingOf(b.x - a.x, b.z - a.z);
      const want = dog ? -Math.atan2(b.z - a.z, b.x - a.x) : Math.atan2(b.x - a.x, b.z - a.z);
      w.m.rotation.y = turnTo(w.m.rotation.y, want, 1 - Math.exp(-dt * 8));
      u.phase += moved * (dog ? 9 / u.size : 4.5);
      if (dog) {
        u.legs.forEach((l, n) => { l.rotation.z = Math.sin(u.phase + (n % 2) * Math.PI + (n > 1 ? 0.6 : 0)) * 0.6; });
        u.tail.rotation.x = Math.sin(u.phase * 2) * 0.6;
        u.head.rotation.z = lerp(u.head.rotation.z, 0, 0.1);
        u.body.rotation.z = lerp(u.body.rotation.z, 0, 0.2);
        u.body.position.y = Math.abs(Math.sin(u.phase)) * 0.02;
      } else {
        u.legL.rotation.x = Math.sin(u.phase) * 0.5; u.legR.rotation.x = -Math.sin(u.phase) * 0.5;
        u.armL.rotation.x = -Math.sin(u.phase) * 0.4; u.armR.rotation.x = Math.sin(u.phase) * 0.4;
        u.body.position.y = Math.abs(Math.cos(u.phase)) * 0.025;
      }
      // "Fine, quickly": a phone call halfway over
      if (w.callAt && onSlab(x, z) && (w.callAt -= 1) <= 0) { w.callAt = 0; w.pause = 3.5; w.pose = 'phone'; }
      w.dist = (w.dist || 0) + moved;
      const spacing = dog ? 0.42 * u.size : 0.72;
      if (w.dist > spacing) {
        w.dist = 0;
        stamp(dog ? 'paw' : 'boot', x, z, w.heading);
        if (hyp(x, z, player.x, player.z) < 14 && !dog) sfx(onSlab(x, z) && gs.poured && gs.H < 40 ? 'soft' : 'gravel', x, z);
      }
    }
    // the drivers stand by their trucks, shifting their weight
    pumpGuy.userData.body.position.y = Math.sin(toolT * 1.3) * 0.004;
    if (mixer.visible && !drives.some((d) => d.group === mixer) && gs.truck) {
      mixGuy.visible = true;
      mixGuy.position.set(mixer.position.x + (day.boom ? -3.2 : 4.4), 0, mixer.position.z + 1.9);
      mixGuy.rotation.y = -1.9;
    } else mixGuy.visible = false;
  }

  function updateWorld(dt) {
    updateBoom(dt);
    for (let k = drives.length - 1; k >= 0; k--) {
      const d = drives[k];
      d.t += dt / d.seconds;
      const e = 1 - Math.pow(1 - Math.min(1, d.t), 3);
      d.group.position.x = lerp(d.from, d.to, e);
      if (d.t >= 1) { drives.splice(k, 1); if (d.done) d.done(); }
    }
    updateWalkers(dt);
    // the drum turns about its own axis: slowly one way to keep the load mixed, faster the other
    // way to bring it up and out while it pours
    if (mixer.visible) drumSpin.rotation.x += dt * (stream.visible ? -2.4 : 0.7);
    if (tripod.visible) laserHead.rotation.y += dt * 6;
    beam.visible = gs.laserOn && laserWorks();
    // blowout drains the edge
    if (gs.blowout && gs.phase === 'pour') {
      gs.cells.forEach((c) => { if (gs.blowout.cells.has(c.idx) && c.fill > 0) { c.fill = Math.max(0, c.fill - 14 * dt); } });
      cellsDirty = true;
    }
    // soup levels itself, slowly
    if (gs.phase === 'pour' && (gs.mixState === 'soup' || gs.water)) {
      const k = (gs.mixState === 'soup' ? 0.35 : 0.12) * dt;
      for (const c of gs.cells) {
        if (isOn(c.i + 1, c.j)) { const r = gs.grid[c.idx + 1]; const f = (c.fill - r.fill) * k; c.fill -= f; r.fill += f; }
        if (isOn(c.i, c.j + 1)) { const d = gs.grid[c.idx + NX]; const f = (c.fill - d.fill) * k; c.fill -= f; d.fill += f; }
      }
      cellsDirty = true;
    }
    // too tired to stand
    if (gs.energy < 8 && gs.phase !== 'end' && gs.t - gs.lastSleep > 120 && !modalOpen) {
      gs.lastSleep = gs.t;
      const m = irnd(20, 35);
      simulate(m, true);
      gs.energy = clamp(gs.energy + 20, 0, 100);
      toast(L.sleepy.replace('{m}', m), 'warn');
      remember('You fell asleep standing up.');
    }
    // changing the pans for blades: a few spanner turns
    if (gs.fitting) {
      const f = gs.fitting, now = performance.now();
      if (now > f.next) { f.next = now + 650; sfx('clank', gs.tools[f.id].x, gs.tools[f.id].z); }
      if (now >= f.until) {
        gs.fit[f.id] = f.to;
        gs.fitting = null;
        toast(`${f.to === 'blades' ? 'Blades' : 'Pans'} on the ${TOOLS[f.id].name.toLowerCase()}.`, 'good');
      }
    }
    if (gs.fastForward === 'pump' && gs.pumpHere) { gs.fastForward = null; showWait(); }
    if (gs.fastForward === 'truck' && gs.truck) { gs.fastForward = null; showWait(); }
  }

  /** Splashes, spray, rain and smoke, from whatever is making them. */
  const nozzleW = new THREE.Vector3();
  let smokeT = 0;
  function updateEffects(dt) {
    if (CALM) return;
    if (stream.visible && target) {
      const y = groundY(target._hx, target._hz);
      for (let k = 0; k < 3; k++) emit(target._hx + rnd(-0.15, 0.15), y + 0.02, target._hz + rnd(-0.15, 0.15), rnd(-1.2, 1.2), rnd(0.8, 2.2), rnd(-1.2, 1.2), 0.6, 0x7f8285, rnd(0.03, 0.06));
    }
    if (markerAnim() === 'wash') {
      nozzleW.set(0.3, -0.35, -1.1);
      camera.localToWorld(nozzleW);
      camera.getWorldDirection(tmpV);
      for (let k = 0; k < 6; k++) emit(nozzleW.x, nozzleW.y, nozzleW.z, tmpV.x * 5 + rnd(-0.6, 0.6), tmpV.y * 5 + rnd(0, 1.2), tmpV.z * 5 + rnd(-0.6, 0.6), 0.7, 0xd9ecf7, rnd(0.015, 0.03));
    }
    if (gs.blowout && gs.phase === 'pour') {
      const b = gs.blowout.p;
      for (let k = 0; k < 3; k++) emit(b.x + rnd(-0.5, 0.5), 0.25, b.z + rnd(-0.5, 0.5), rnd(-1, 1), rnd(0.5, 1.5), rnd(-1, 1), 0.7, 0x85888a, 0.07);
    }
    if (performance.now() < rainUntil) {
      for (let k = 0; k < 14; k++) emit(player.x + rnd(-12, 12), rnd(6, 10), player.z + rnd(-12, 12), 0.6, -11, 0, 1.2, 0xb8c6d3, 0.02, -2);
    }
    smokeT -= dt;
    if (smokeT <= 0) {
      smokeT = 0.18;
      const pumping = pump.visible && gs.pumpHere && gs.phase === 'pour' && gs.truck && !gs.truck.waiting;
      if (pump.visible) emit(pump.position.x - 2.2, 3.1, pump.position.z - 1.0, rnd(-0.2, 0.2), rnd(0.8, 1.3), rnd(-0.2, 0.2), 1.5, pumping ? 0x3a3d40 : 0x6d7074, pumping ? 0.22 : 0.12, 0.25);
      if (mixer.visible) emit(mixer.position.x - 2.4, 3.1, mixer.position.z - 1.0, rnd(-0.2, 0.2), rnd(0.8, 1.3), rnd(-0.2, 0.2), 1.5, 0x6d7074, 0.14, 0.25);
      if (mpos.on && chance(0.5)) emit(mpos.x + rnd(-0.1, 0.1), groundY(mpos.x, mpos.z) + 0.6, mpos.z, rnd(-0.1, 0.1), 0.5, rnd(-0.1, 0.1), 1.0, 0x9a9da0, 0.025, 0.2);
      for (const d of drives) emit(d.group.position.x + 4, 0.3, d.group.position.z + rnd(-1, 1), rnd(0.5, 1.5), rnd(0.2, 0.6), rnd(-0.5, 0.5), 1.4, 0xa89478, 0.35, 0.2);
    }
  }

  // light and sky by the clock
  const SKY = [[0, 0x0a0f1c], [300, 0x101a30], [360, 0xd88f64], [450, 0xa7c6e2], [720, 0x8fbde6], [1050, 0xa8c2da], [1140, 0xe08e5a], [1230, 0x1a2140], [1440, 0x0a0f1c]];
  const cA = new THREE.Color(), cB = new THREE.Color(), grey = new THREE.Color(0x8e959c);
  function skyAt(t) {
    const m = ((t % 1440) + 1440) % 1440;
    for (let k = 0; k < SKY.length - 1; k++) {
      if (m >= SKY[k][0] && m <= SKY[k + 1][0]) {
        const f = (m - SKY[k][0]) / (SKY[k + 1][0] - SKY[k][0]);
        return cA.setHex(SKY[k][1]).lerp(cB.setHex(SKY[k + 1][1]), f);
      }
    }
    return cA.setHex(SKY[0][1]);
  }
  const skyVis = new THREE.Color(), skyTop = new THREE.Color(), deep = new THREE.Color(0x1d4f8c), night = new THREE.Color(0x03060d);
  const sunDir = new THREE.Vector3();
  function light(t) {
    const sky = skyVis.copy(skyAt(t));
    if (day && day.rh > 78) sky.lerp(grey, 0.35 * (day.rh - 78) / 17);
    scene.background = sky;
    const m = ((t % 1440) + 1440) % 1440;
    const dayness = clamp(Math.sin(Math.PI * (m - 330) / 840), 0, 1);
    // the horizon is the colour of the fog, so the far trees melt into it; overhead is deeper
    skyUni.bottom.value.copy(sky).convertSRGBToLinear();
    skyTop.copy(sky).lerp(dayness > 0.1 ? deep : night, 0.45);
    if (day && day.rh > 78) skyTop.lerp(grey, 0.3);
    skyUni.top.value.copy(skyTop).convertSRGBToLinear();
    // fog is mixed in after the output encoding, so it takes the colour as it is seen
    scene.fog.color.copy(sky);
    skyDome.position.copy(camera.position);
    stars.position.copy(camera.position);
    stars.material.opacity = clamp(1 - dayness * 3, 0, 1) * (day && day.rh > 85 ? 0.3 : 0.9);
    hemi.intensity = 0.3 + 0.6 * dayness;
    sun.intensity = 0.95 * dayness;
    const ang = Math.PI * (m - 330) / 840;
    sunDir.set(Math.cos(ang) * 60, Math.sin(ang) * 70, 25).normalize();
    // the shadow square follows you round the site
    const px = gs && gs.phase !== 'title' ? player.x : 0, pz = gs && gs.phase !== 'title' ? player.z : 0;
    sun.target.position.set(px, 0, pz);
    sun.position.set(px + sunDir.x * 90, Math.max(8, sunDir.y * 90), pz + sunDir.z * 90);
    sun.castShadow = dayness > 0.06;
    sunDisc.position.copy(camera.position).addScaledVector(sunDir, 300);
    sunDisc.visible = sunDir.y > -0.08;
    sunDisc.material.opacity = day && day.rh > 85 ? 0.35 : 1;
    moonDisc.position.copy(camera.position).add(tmpV2.set(-sunDir.x * 0.8, Math.max(0.25, -sunDir.y), -sunDir.z - 0.4).normalize().multiplyScalar(300));
    moonDisc.visible = dayness < 0.2;
    const dark = dayness < 0.25;
    flood.intensity = dark ? 1.4 : 0;
    lamp.material.color.setHex(dark ? 0xfff3d6 : 0x777777);
    headMat.color.setHex(dark ? 0xfff6d8 : 0x666666);
    beamMat.opacity = dark ? 0.07 : 0;
    const lit = clamp(1 - dayness * 2.5, 0, 1);
    townMats.forEach((mt) => { mt.emissiveIntensity = lit * 0.9; });
  }

  function placeCamera(dt) {
    let y = EYE;
    const c = cellAt(player.x, player.z);
    // standing on it once it carries you; in it, up to the ankles, while it is wet
    if (c) y += groundY(player.x, player.z) * (gs.phase === 'pour' ? 0.3 : 0.9);
    if (gs.tool === 'rideOn') y = 1.62 + groundY(player.x, player.z);
    const knelt = kneeling();
    if (knelt && !wasKneeling && gs.poured && onSlab(player.x, player.z)) stamp('knee', player.x, player.z, player.yaw, true);
    wasKneeling = knelt;
    kneel = lerp(kneel, knelt ? 1 : 0, 1 - Math.exp(-dt * 6));
    y -= kneel * 0.85;
    let roll = 0;
    if (player.fall > 0) {
      player.fall -= dt;
      const f = player.fall > 1.2 ? (1.8 - player.fall) / 0.6 : player.fall > 0.4 ? 1 : player.fall / 0.4;
      y -= 1.2 * f;
      roll = 0.5 * f;
    }
    if (gs.waitMode === 'van') y = 1.55;
    const bob = CALM ? 0 : Math.sin(player.bob) * 0.035;
    camera.position.set(player.x, y + (player.moving ? bob : 0), player.z);
    camera.rotation.set(player.pitch, player.yaw, roll);
    camera.updateMatrixWorld();
    placeTools(dt);
    updatePack(dt);
  }

  // ------------------------------------------------------------------ the boom
  const BOOM_AT = P(11, -4.5);           // where a boom pump sets up: cab to the slab, legs out
  const BOOM_REACH = BOOM_L * 3 * 0.97;
  const boomAim = new THREE.Vector3(NaN, 0, 0);
  const boomJ = [0, 1, 2, 3].map(() => new THREE.Vector3());
  const bF = [0, 1, 2].map(() => new THREE.Vector3()), bR = [0, 1, 2].map(() => new THREE.Vector3());
  let boomSpeed = 0;
  /** 0 folded on the truck, 1 out over the slab: up after it arrives, back down before it leaves. */
  function boomUnfold() {
    if (!day.boom || !pump.visible) return 0;
    if (gs.boomFoldAt) return 1 - clamp((gs.t - gs.boomFoldAt) / 6, 0, 1);
    if (gs.boomSetAt) return clamp((gs.t - gs.boomSetAt) / 10, 0, 1);
    return 0;
  }
  function updateBoom(dt) {
    const on = !!day.boom && pump.visible;
    boomParts.visible = on;
    boomSecs.forEach((m) => { m.visible = on; });
    if (!on) { boomSpeed = 0; return; }
    const u = boomUnfold();
    // the legs go out first, then the boom lifts off its rest
    const legs = clamp(u * 3, 0, 1), up = clamp((u - 0.3) / 0.7, 0, 1);
    outriggers.forEach((o) => {
      const reach = 1 + legs * 1.6;
      o.beam.scale.z = reach; o.beam.position.z = o.sd * 0.5 * reach;
      o.leg.position.z = o.sd * reach; o.leg.scale.y = 0.5 + legs * 0.5; o.leg.position.y = -0.2 - legs * 0.15;
    });
    // the tip goes over the hose end: in your hands, where you left it, or the edge of the slab.
    // The pump driver steers it with the remote, a beat behind you.
    const hose = gs.tools.hose;
    let tx = SLAB.x1 - 1.5, tz = gz(ENTRY.j) + 0.5;
    if (hose && hose.in === 'hand') { tx = player.x; tz = player.z; } else if (hose && hose.in === 'ground') { tx = hose.x; tz = hose.z; }
    if (isNaN(boomAim.x)) boomAim.set(tx, 0, tz);
    const k = 1 - Math.exp(-dt * 1.2);
    const before = boomTip.clone();
    boomAim.x = lerp(boomAim.x, tx, k); boomAim.z = lerp(boomAim.z, tz, k);
    const b = pump.position;
    const J0 = boomJ[0].set(b.x + TURRET.x, TURRET.y, b.z + TURRET.z);
    // folded: zig-zag over the cab
    bF[0].set(J0.x - 9.3, J0.y + 0.35, J0.z); bF[1].set(J0.x, J0.y + 0.7, J0.z); bF[2].set(J0.x - 9.3, J0.y + 1.05, J0.z);
    // out: the first section up steep for a near pour, flatter for a far one; the other two
    // bend to put the tip above the hose end
    const dx = boomAim.x - J0.x, dz = boomAim.z - J0.z, d0 = Math.hypot(dx, dz) || 0.01, ux = dx / d0, uz = dz / d0;
    const d = Math.min(d0, BOOM_REACH), H = 5.4 - J0.y;
    const a1 = lerp(1.2, 0.22, clamp(d / 26, 0, 1));
    const p1x = BOOM_L * Math.cos(a1), p1y = BOOM_L * Math.sin(a1);
    const vx = d - p1x, vy = H - p1y, D = clamp(Math.hypot(vx, vy), 0.5, BOOM_L * 2 - 0.01);
    const a2 = Math.atan2(vy, vx) + Math.acos(clamp(D / (2 * BOOM_L), -1, 1));
    const p2x = p1x + BOOM_L * Math.cos(a2), p2y = p1y + BOOM_L * Math.sin(a2);
    const a3 = Math.atan2(H - p2y, d - p2x);
    const p3x = p2x + BOOM_L * Math.cos(a3), p3y = p2y + BOOM_L * Math.sin(a3);
    const W = (v, r, y) => v.set(J0.x + ux * r, J0.y + y, J0.z + uz * r);
    W(bR[0], p1x, p1y); W(bR[1], p2x, p2y); W(bR[2], p3x, p3y);
    for (let n = 0; n < 3; n++) { boomJ[n + 1].lerpVectors(bF[n], bR[n], up); stretch(boomSecs[n], boomJ[n], boomJ[n + 1]); }
    boomTip.copy(boomJ[3]);
    boomSpeed = before.lengthSq() ? before.distanceTo(boomTip) / Math.max(dt, 0.001) : 0;
  }

  // ------------------------------------------------------------------ tools, moving
  let toolT = 0, cupT = 0, lastSwing = 0, kneel = 0, wasKneeling = false;
  const handPos = new THREE.Vector3();
  /** What the hands are doing at a job marker, when they are doing one. */
  function markerAnim() {
    if (!nearMarker || !input.action || lastCtxKind !== 'marker') return null;
    const id = nearMarker.id;
    if (/^form|^block|^blowout/.test(id)) return 'hammer';
    if (id.startsWith('edge')) return gs.tool === 'handTrowel' ? 'edger' : null;
    if (id.startsWith('tie')) return 'tie';
    if (id.startsWith('cut')) return 'cut';
    if (id === 'wash') return 'wash';
    return null;
  }
  function onTarp(x, z) { return tarp.visible && Math.abs(x - POS.tarp.x) < 1.45 && Math.abs(z - POS.tarp.z) < 0.7; }
  /** Down on one knee: hand troweling, and tying the mesh. */
  function kneeling() { const a = markerAnim(); return a === 'edger' || a === 'tie'; }
  function moveMachine(t, R, tx, tz, running) {
    // a spot that already doesn't fit (lifted over a board, say) lets it go anywhere
    const stuck = !discFits(t.x, t.z, R, running);
    const ok = (x, z) => stuck || discFits(x, z, R, running);
    if (ok(tx, tz)) { t.x = tx; t.z = tz; } else if (ok(tx, t.z)) t.x = tx; else if (ok(t.x, tz)) t.z = tz;
  }
  function placeTools(dt) {
    toolT += dt;
    const t = gs.tool;
    const anim = markerAnim();
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    const sway = player.moving && !CALM ? Math.sin(player.bob) * 0.015 : 0;
    // on a tall screen the right hand comes in, or it would be holding things off the edge of it
    const halfW = 0.62 * Math.tan((camera.fov * Math.PI) / 360) * camera.aspect;
    const hx = Math.min(0.32, halfW * 0.6), hy = camera.aspect < 1 ? -0.3 : -0.34;
    hands.position.set(hx + sway, hy + Math.abs(sway) * 0.6, -0.62);
    viewTools.pipe.visible = gs.carrying === 'pipe';
    viewTools.pipe.rotation.z = sway * 2;
    viewTools.laser.visible = gs.carrying === 'laser';
    viewTools.laser.rotation.z = sway * 2;
    viewTools.hose.visible = (t === 'hose' && !gs.carrying) || anim === 'wash';
    viewTools.hammer.visible = t === 'hammer';
    viewTools.pliers.visible = t === 'pliers';
    viewTools.cutter.visible = t === 'cutter';
    // the hose kicks with every stroke of the pump
    if (viewTools.hose.visible) {
      const kick = (stream.visible || anim === 'wash') && !CALM ? Math.sin(toolT * 50) * 0.008 + Math.max(0, Math.sin(toolT * 7.5)) * 0.025 : 0;
      viewTools.hose.position.set(kick * 0.4, kick, 0);
      viewTools.hose.rotation.x = anim === 'wash' ? -0.35 : 0;
    }
    if (anim === 'hammer') {
      // wind up slow, come down fast, bounce: about two and a half a second
      const ph = (toolT * 2.4) % 1;
      const a = ph < 0.6 ? lerp(-1.1, 0.7, ph / 0.6) : ph < 0.72 ? lerp(0.7, -1.2, (ph - 0.6) / 0.12) : lerp(-1.2, -1.1, (ph - 0.72) / 0.28);
      viewTools.hammer.rotation.x = a;
      if (ph >= 0.72 && lastSwing < 0.72) sfx(nearMarker.id === 'block' ? 'clank' : 'hammer');
      lastSwing = ph;
    } else { lastSwing = 0; viewTools.hammer.rotation.x = -0.6; }
    // at a corner or an edge the trowel is on the concrete itself, sweeping arcs into the corner
    // and along the edge; the view eases round to it, as you would look at what you are doing
    workTrowel.visible = workArm.visible = anim === 'edger';
    if (anim === 'edger') {
      const e = site.edges[+nearMarker.id.slice(4)] || {};
      const inX = e.inX || 0, inZ = e.inZ || 0;
      const sw = Math.sin(toolT * 5), al = e.alongX !== undefined ? P(e.alongX, e.alongZ) : P(-inZ, inX);
      const wx = nearMarker.x + al.x * sw * 0.18 + inX * (0.06 + Math.cos(toolT * 5) * 0.04);
      const wz = nearMarker.z + al.z * sw * 0.18 + inZ * (0.06 + Math.cos(toolT * 5) * 0.04);
      workTrowel.position.set(wx, groundY(wx, wz) + 0.006, wz);
      workTrowel.rotation.set(0, Math.atan2(-al.z, al.x) + sw * 0.35, 0.04);
      handPos.set(0.22, -0.42, -0.25);
      camera.localToWorld(handPos);
      tmpV2.set(wx, workTrowel.position.y + 0.06, wz);
      stretch(workArm, handPos, tmpV2);
      const want = Math.atan2(-(nearMarker.x - player.x), -(nearMarker.z - player.z));
      const d = Math.max(0.3, hyp(nearMarker.x, nearMarker.z, player.x, player.z));
      const k = 1 - Math.exp(-dt * 3);
      player.yaw = turnTo(player.yaw, want, k);
      player.pitch = lerp(player.pitch, -Math.atan2(camera.position.y - groundY(nearMarker.x, nearMarker.z), d), k);
    }
    viewTools.handTrowel.visible = t === 'handTrowel' && anim !== 'edger';
    // working a spot with it: sweeps arcs, the blade's leading edge lifted
    if (t === 'handTrowel' && input.action && lastCtxKind === 'repair') {
      viewTools.handTrowel.position.set(-0.05 + Math.sin(toolT * 5) * 0.12, -0.2, -0.25 + Math.cos(toolT * 5) * 0.04);
      viewTools.handTrowel.rotation.set(0.05, Math.sin(toolT * 5) * 0.5, Math.sin(toolT * 5) * 0.08);
    } else { viewTools.handTrowel.position.set(-0.05, -0.16, -0.2); viewTools.handTrowel.rotation.set(0.35, 0, 0); }
    // pliers: twist, twist, twist
    // (the pliers turn about their own length; the coil in the other hand stays put)
    if (anim === 'tie') {
      pliersBody.rotation.set(0.7, 0.4, -0.3 + Math.sin(toolT * 14) * 0.9);
      viewTools.pliers.position.set(-0.16, 0, -0.16);
      if (Math.sin(toolT * 14) > 0.95 && chance(0.5)) sfx('twist');
    } else { pliersBody.rotation.set(0.5, 0.4, -0.3); viewTools.pliers.position.set(-0.2, 0.1, -0.1); }
    // the cutter's handles squeeze together, the jaws close
    const squeeze = anim === 'cut' ? (0.5 + 0.5 * Math.sin(toolT * 3)) : 0;
    const cp = viewTools.cutter.userData.parts;
    cp.arms.forEach((arm, k) => { arm.rotation.y = (k ? 1 : -1) * 0.1 * (1 - squeeze); });
    cp.jaws.forEach((jaw, k) => { jaw.rotation.y = (k ? -1 : 1) * 0.14 * (1 - squeeze); });
    // coffee: up, tip, down
    viewTools.cup.visible = cupT > 0;
    if (cupT > 0) {
      cupT -= dt;
      const up = Math.sin(clamp((1.6 - cupT) / 1.6, 0, 1) * Math.PI);
      viewTools.cup.position.set(-0.25 * up, 0.22 * up, 0.1 * up);
      viewTools.cup.rotation.x = up * 0.9;
    }

    // hand tools lying about
    ['float', 'handTrowel', 'hammer', 'pliers', 'cutter', 'hose'].forEach((id) => {
      const tl = gs.tools[id], g = lying[id];
      g.visible = !!tl && tl.in === 'ground';
      if (g.visible) { g.position.set(tl.x, groundY(tl.x, tl.z) + (onTarp(tl.x, tl.z) ? 0.013 : 0), tl.z); g.rotation.y = tl.yaw; }
    });

    // the machines: parked where they were left, or running where you steer them
    const running = input.action && lastCtxKind === 'trowel';
    ['trowelSmall', 'trowelBig', 'rideOn'].forEach((id) => {
      const m = machines[id], tl = gs.tools[id];
      m.group.visible = !!tl && tl.in !== 'van';
      if (!m.group.visible) return;
      const inHand = tl.in === 'hand';
      if (inHand && m.twin) {
        tl.yaw = player.yaw;
        tl.x = player.x + fx * 0.3; tl.z = player.z + fz * 0.3;
      } else if (inHand) {
        // a walk-behind runs where you look, between your boots and an arm's length out
        camera.getWorldDirection(tmpV);
        let dist = 2.1;
        if (tmpV.y < -0.05) dist = clamp(Math.hypot(tmpV.x, tmpV.z) * ((camera.position.y - 0.1) / -tmpV.y), 1.8, 2.3);
        if (!gs.fitting) moveMachine(tl, m.R, player.x + fx * dist, player.z + fz * dist, running);
        tl.yaw = Math.atan2(player.x - tl.x, player.z - tl.z);
      }
      const on = inHand && running;
      m.spin = lerp(m.spin, on ? 17 : inHand && !gs.fitting ? 5 : 0, 1 - Math.exp(-dt * 3));
      const fit = gs.fit[id];
      m.rotors.forEach((r, k) => {
        r.rotor.rotation.y += (k ? 1 : -1) * m.spin * dt;
        r.pan.visible = fit === 'pans';
        r.blades.visible = fit === 'blades';
        // the blades tilt up as the concrete gets harder, the way a finisher sets them
        r.blades.children.forEach((arm) => { arm.children[0].rotation.x = 0.04 + clamp((gs.H - 55) / 40, 0, 1) * 0.16; });
      });
      const buzz = inHand && !CALM && !gs.fitting ? (on ? Math.sin(toolT * 71) * 0.004 : Math.sin(toolT * 43) * 0.0015) : 0;
      m.group.position.set(tl.x, groundY(tl.x, tl.z) + buzz, tl.z);
      // tipped back on its handle while the pans or blades are changed
      const tip = gs.fitting && gs.fitting.id === id ? -0.35 : 0;
      m.group.rotation.set(m.twin ? 0 : tip, tl.yaw, on && !CALM ? Math.sin(toolT * 11) * 0.012 : 0, 'YXZ');
      if (on && gs.H < 70 && chance(dt * 30 * m.rotors.length)) {
        const d = discs()[irnd(0, m.rotors.length - 1)];
        const a = rnd(0, Math.PI * 2);
        const px = d.x + Math.cos(a) * d.r, pz = d.z + Math.sin(a) * d.r;
        emit(px, groundY(px, pz) + 0.03, pz, -Math.sin(a) * 2.2, rnd(0.4, 1.1), Math.cos(a) * 2.2, 0.5, 0x8d9094, 0.035);
      }
    });
    mpos.on = isMachine(t) && player.fall <= 0;
    if (mpos.on) { mpos.x = gs.tools[t].x; mpos.z = gs.tools[t].z; }

    // the float: a plate on the concrete, a pole back to your hands; it sweeps while you work it
    const floatOn = t === 'float' && player.fall <= 0;
    floatTool.visible = floatPole.visible = floatOn;
    if (floatOn) {
      let px = target ? target._hx : player.x + fx * 1.8, pz = target ? target._hz : player.z + fz * 1.8;
      const working = input.action && (lastCtxKind === 'level' || lastCtxKind === 'repair');
      const sweep = working && !CALM ? Math.sin(toolT * 4.4) * 0.38 : 0;
      px += fx * sweep; pz += fz * sweep;
      // a pipe through the slab stops the plate: it slides back along the stroke to clear it
      const ax = Math.cos(player.yaw), az = -Math.sin(player.yaw), bx = Math.sin(player.yaw), bz = Math.cos(player.yaw);
      for (const pen of site.pens) {
        const dx = pen.x - px, dz = pen.z - pz;
        const lx = dx * ax + dz * az, lz = dx * bx + dz * bz;
        if (Math.abs(lx) < 0.55 && Math.abs(lz) < 0.2) { const push = lz - Math.sign(lz || 1) * 0.2; px += bx * push; pz += bz * push; }
      }
      floatTool.position.set(px, groundY(px, pz) + 0.004, pz);
      // the leading edge up, whichever way it is going
      floatTool.rotation.set(working ? Math.cos(toolT * 4.4) * 0.05 : 0, player.yaw, 0, 'YXZ');
      handPos.set(0.26, -0.5, -0.3);
      camera.localToWorld(handPos);
      tmpV2.set(px, floatTool.position.y + 0.03, pz);
      stretch(floatPole, handPos, tmpV2);
    }
  }

  // ------------------------------------------------------------------ HUD
  let hudT = 0;
  let hudTopH = 0;
  const mapCtx = $('#map').getContext('2d');
  function objective() {
    const nextPrep = () => {
      const p = gs.prep, left = [];
      if (!p.unload) left.push('unload the tools');
      if (p.form.includes(false)) left.push('check the formwork (hammer)');
      if (!p.laser) left.push('set up the laser');
      if (site.ties.some((t) => !t.done)) left.push('tie the loose mesh (pliers & wire)');
      if (site.cuts.some((t) => !t.done)) left.push('cut the bar sticking up (rebar cutter)');
      return left;
    };
    switch (gs.phase) {
      case 'prep': {
        const left = nextPrep();
        const due = `Pump due ${clock(gs.pumpAt)}`;
        return left.length ? `Before the pump: ${left.join(', ')}.<small>${due}</small>` : `Prep done. Coffee, or wait for the pump.<small>${due}</small>`;
      }
      case 'pipes':
        if (gs.pipes < 6 && day.boom) return `The pump driver is setting up the boom.<small>${nextPrep().length ? `Still open: ${nextPrep().join(', ')}` : 'Nothing to carry today. Coffee?'}</small>`;
        if (gs.pipes < 6) return (gs.carrying ? `Carry the pipe to marker ${gs.pipes + 1}.` : `Grab a pipe from the pile (${gs.pipes}/6 laid).`) + (nextPrep().length ? `<small>Still open: ${nextPrep().join(', ')}</small>` : '');
        return `Line laid. Waiting for the mixer.<small>Truck due ${clock(gs.nextTruckAt)}</small>`;
      case 'pour': {
        if (gs.blocked >= 0) return 'The line is blocked. Find the orange marker and hit the pipe!';
        if (gs.blowout) return `The formwork burst on the ${gs.blowout.name}. Fix it before you lose more!`;
        if (!laserWorks() && gs.prep.laser) return 'Laser batteries are dead. Spares are in the van.';
        const f = Math.round(filledShare() * 100);
        const sub = gs.tool !== 'hose' && gs.tools.hose && gs.tools.hose.in === 'ground' && gs.truck && !gs.truck.waiting ? 'Pick up the hose at the end of the line.' : gs.truck && !gs.truck.waiting ? `Hose: pour · Float (tarp): level · Laser shows the height` : `Next truck due ${clock(gs.nextTruckAt)}. Float what you have.`;
        return `Pour to ${day.thick} mm: ${f}% filled, ±${rms().toFixed(1)} mm.<small>${sub}</small>`;
      }
      case 'wash': if (gs.gaveUp) return gs.packing ? 'Throwing the tools in the van.' : 'It\'s gone off, tools unwashed. Walk to the van and go home.<small>They\'ll be concrete tools now. Very sturdy.</small>'; return 'Wash your tools at the water tank before they set.<small>The pump driver does his own pipes. The laser can be packed up any time now.</small>';
      case 'cure': {
        if (gs.packing) return 'Throwing the tools in the van.<small>Therapy, but cheaper.</small>';
        if (gs.gaveUp) return 'It\'s gone off. Nothing more goes on this slab today.<small>Walk to the van: the tools go in, you go home.</small>';
        const marks = gs.cells.filter((c) => c.marks.length && !c.defect).length;
        const warn = marks && gs.H < 80 ? `<small>${marks} m² with marks — ${gs.H < 50 ? 'float or pan' : 'pan'} them out before 80%.</small>` : '';
        if (gs.H < 25) return `Let it harden. Guard it, eat, or nap.${warn || '<small>Pans from 25%.</small>'}`;
        const mt = machineTool();
        if (!gs.panPasses.length) {
          if (!mt) return `Pan pass: take a power trowel from beside the tarp.${warn || '<small>They come with pans on.</small>'}`;
          if (gs.fit[mt] !== 'pans') return `Fit the pans (the button next to Put down).${warn}`;
          return `Run it over every orange square.${warn || '<small>Hold the big button; slide your thumb on it to steer.</small>'}`;
        }
        if (gs.edgesDone < site.edges.length) return `Edges, corners and collars (${gs.edgesDone}/${site.edges.length}).${warn || '<small>Hand trowel from the tarp; the edge trowel does straight edges.</small>'}`;
        if (!gs.bladePasses.length) {
          if (gs.H < 55) return `Wait for blades (55%).${warn || '<small>Another pan pass flattens it more.</small>'}`;
          if (!mt) return `Blade pass: take a power trowel and fit the blades.${warn}`;
          if (gs.fit[mt] !== 'blades') return `Fit the blades (the button next to Put down).${warn}`;
          return `Blades: every orange square again.${warn}`;
        }
        if (gs.prep.laser && !gs.laserPacked) return `Pack up the laser and put it in the van.<small>${gs.H < 95 ? `${dur(etaTo(95))} to 95%.` : 'Then home.'}</small>`;
        if (gs.H < 95) return `Wait for 95%, then go home.<small>${dur(etaTo(95))} to go. Another blade pass shines it up.</small>`;
        return 'It\'s 95%. Walk to the van and go home.';
      }
      default: return '';
    }
  }
  function updateHUD(dt) {
    hudT -= dt;
    if (hudT > 0) return;
    hudT = 0.12;
    $('#clock').textContent = clock(gs.t);
    $('#phase').textContent = { morning: 'Morning', prep: 'Prep', pipes: 'Pipes', pour: 'Pour', wash: 'Wash up', cure: gs.H >= 25 ? 'Trowel' : 'Curing', end: 'Home' }[gs.phase] || '';
    $('#objective').innerHTML = objective();
    // everything under the task box moves down when its text runs to another line
    const topH = $('#top').offsetHeight;
    if (topH !== hudTopH) { hudTopH = topH; $('#hud').style.setProperty('--hud-top', `${10 + topH + 8}px`); }
    const T = tempAt(gs.t);
    $('#weather').innerHTML = `${T.toFixed(1)} °C <span>·</span> ${day.rh}% RH <span>·</span> wind ${day.wind}<br><span>Slab</span> ${day.thick} mm <span>· energy</span>` +
      `<div id="energyRow"><div id="energyBar"><div id="energyFill" style="width:${gs.energy}%;background:${gs.energy < 25 ? '#ff3b30' : gs.energy < 50 ? '#ffd23f' : '#6bd68a'}"></div></div><span>${gs.cups} cups</span></div>`;
    const hard = $('#hard');
    hard.hidden = !gs.poured;
    if (gs.poured) {
      $('#hardPct').textContent = `${Math.floor(gs.H)}%`;
      $('#hardFill').style.width = `${gs.H}%`;
      const eta = gs.H < 25 ? `Pans in <b>${dur(etaTo(25))}</b>` : gs.H < 55 ? `Blades in <b>${dur(etaTo(55))}</b>` : gs.H < 95 ? `95% in <b>${dur(etaTo(95))}</b>` : '<b>Hard enough to leave</b>';
      const passes = gs.phase === 'cure' ? `<br>Pans ${gs.panPasses.length} · blades ${gs.bladePasses.length} · edges ${gs.edgesDone}/${site.edges.length}<br>Flatness <b>±${rms().toFixed(1)} mm</b>` : '';
      const cov = machineTool() ? `<br>${fitted() === 'pans' ? 'Pan' : 'Blade'} pass <b>${Math.round(passCoverage(passKey()) * 100)}%</b>` : '';
      $('#hardEta').innerHTML = eta + passes + cov;
    }
    const ti = $('#truckInfo');
    ti.hidden = !(gs.phase === 'pour' || (gs.phase === 'pipes' && gs.pipes === 6));
    if (!ti.hidden) {
      const need = gs.cells.reduce((sum, c) => sum + Math.max(0, day.thick - c.fill), 0) / 1000;
      ti.innerHTML = gs.truck ? `Truck ${gs.truck.no}: <b>${Math.max(0, gs.truck.left).toFixed(1)} m³</b> left<br><span style="color:#aeb2b8">low spots need ≈ ${need.toFixed(1)} m³</span>`
        : `Next truck <b>${clock(gs.nextTruckAt || gs.t)}</b><br><span style="color:#aeb2b8">low spots need ≈ ${need.toFixed(1)} m³</span>`;
    }
    $('#btnFinish').hidden = !(gs.phase === 'pour' && filledShare() >= 0.97);
    // what is in your hands, and the one thing you can do to it
    const h = held();
    const nt = !gs.carrying && nearTool ? TOOLS[nearTool] : null;
    const toolLabel = gs.carrying ? `Carrying<small>${gs.carrying === 'pipe' ? 'a pipe' : 'the laser'}</small>`
      : nt ? `${nt.ride ? 'Get on' : h ? 'Swap' : 'Pick up'}<small>${nt.name}${nt.machine ? ` · ${gs.fit[nearTool]}` : ''}</small>`
      : !h ? 'Hands<small>empty</small>' : `${TOOLS[h].ride ? 'Get off' : 'Put down'}<small>${TOOLS[h].name}</small>`;
    if (toolLabel !== btnState.tool) {
      btnTool.innerHTML = toolLabel;
      btnTool.classList.toggle('dim', !nt && (!h || !!gs.carrying));
      btnTool.classList.toggle('ready', !!nt);
      btnState.tool = toolLabel;
    }
    const alt = isMachine(h) ? `${gs.fit[h] === 'pans' ? 'Fit blades' : 'Fit pans'}<small>${gs.fit[h]} on</small>` : laserUsable() ? 'Laser' : '';
    if (alt !== btnState.alt) { btnAlt.innerHTML = alt; btnAlt.hidden = !alt; btnState.alt = alt; }
    btnAlt.classList.toggle('on', alt === 'Laser' && gs.laserOn);
    // the waiting badge follows the state, whatever ended the wait
    const waitKey = `${gs.waitMode}|${gs.fastForward}`;
    if (waitKey !== btnState.wait) { btnState.wait = waitKey; showWait(); }
    $('#btnWait').textContent = gs.waitMode || gs.fastForward ? 'Stop' : 'Wait';
    $('#btnCoffee').textContent = `Coffee ${gs.cups}`;
    // what you're looking at
    const info = $('#targetInfo');
    if (target && gs.pourStarted) {
      const d = target.fill - day.thick;
      const name = `${String.fromCharCode(65 + target.i)}${target.j + 1}`;
      if (gs.phase === 'pour' && gs.laserOn && laserWorks()) {
        const cls = Math.abs(d) <= 3 ? 'dev-ok' : d > 0 ? 'dev-hi' : 'dev-lo';
        info.innerHTML = `${name} · <span class="${cls}">${d > 0 ? '+' : ''}${d.toFixed(0)} mm</span>`;
      } else if (gs.phase === 'pour') {
        info.textContent = `${name} · ${target.fill < 5 ? 'empty' : 'looks about right?'}`;
      } else {
        info.textContent = `${name}` + (target.marks.length ? ` · ${target.marks.length} mark${target.marks.length > 1 ? 's' : ''}` : '') + (target.defect ? ' · set in' : '');
      }
    } else info.textContent = nearMarker ? nearMarker.label : '';
    drawMap();
  }
  /*
   * A map that follows you, 60 m across and turned so up is straight ahead. Anything off the
   * edge — the kebab stand, a marker behind the van — sits on the rim in its direction.
   */
  function drawMap() {
    const g = mapCtx, W = 300, half = W / 2, span = 60, s = W / span;
    const cos = Math.cos(player.yaw), sin = Math.sin(player.yaw);
    const toMap = (x, z) => {
      const dx = x - player.x, dz = z - player.z;
      // forward (-sin, -cos) goes up the screen, right (cos, -sin) goes right
      const right = dx * cos - dz * sin, fwd = -dx * sin - dz * cos;
      return [half + right * s, half - fwd * s];
    };
    const onRim = (pt) => {
      const dx = pt[0] - half, dy = pt[1] - half, r = Math.hypot(dx, dy), max = half - 10;
      return r > max ? [half + (dx / r) * max, half + (dy / r) * max, true] : [pt[0], pt[1], false];
    };
    g.clearRect(0, 0, W, W);
    g.save();
    g.beginPath(); g.arc(half, half, half - 2, 0, Math.PI * 2); g.clip();
    g.fillStyle = 'rgba(90,80,64,0.55)';
    g.fillRect(0, 0, W, W);
    const poly = (x0, z0, x1, z1, col) => {
      const pts = [toMap(x0, z0), toMap(x1, z0), toMap(x1, z1), toMap(x0, z1)];
      g.fillStyle = col; g.beginPath(); g.moveTo(pts[0][0], pts[0][1]);
      for (let k = 1; k < 4; k++) g.lineTo(pts[k][0], pts[k][1]);
      g.closePath(); g.fill();
    };
    const v = gs.poured ? Math.round(90 + gs.H * 1.2) : 150;
    const slabCol = gs.pourStarted ? `rgb(${v},${v},${v + 4})` : '#8c8577';
    gs.cells.forEach((c) => poly(gx(c.i) - 0.02, gz(c.j) - 0.02, gx(c.i + 1) + 0.02, gz(c.j + 1) + 0.02, slabCol));
    poly(-28.6, 7.8, -23.4, 10.2, '#e9e7e2');
    poly(-21.6, -9.6, -20.4, -8.4, '#cfe3ea');
    poly(-39.6, -25.4, -36.4, -22.6, '#2f6f6a');
    poly(-37, 11.8, -31, 14.2, '#3c6e9e');
    if (pump.visible) poly(pump.position.x - 4.3, pump.position.z - 1.2, pump.position.x + 4.3, pump.position.z + 1.2, '#f2b705');
    if (mixer.visible) poly(mixer.position.x - 4.5, mixer.position.z - 1.2, mixer.position.x + 4.5, mixer.position.z + 1.2, '#ff6b1a');
    g.restore();
    const pulse = 5 + Math.sin(performance.now() / 200) * 1.5;
    g.fillStyle = '#ff6b1a';
    markers.forEach((m) => {
      if (!m.active()) return;
      const [x, y, rim] = onRim(toMap(m.x, m.z));
      g.beginPath(); g.arc(x, y, rim ? 6 : pulse, 0, Math.PI * 2); g.fill();
    });
    // tools lying about: small white squares; the one a job is asking for, yellow
    TOOL_IDS.forEach((id) => {
      const tl = gs.tools[id];
      if (!tl || tl.in !== 'ground') return;
      const want = id === wantTool;
      const [x, y, rim] = onRim(toMap(tl.x, tl.z));
      if (rim && !want) return;
      g.fillStyle = want ? '#ffd23f' : 'rgba(242,239,232,0.85)';
      const r = want ? pulse : 3.5;
      g.fillRect(x - r, y - r, r * 2, r * 2);
    });
    g.fillStyle = '#5aa9ff';
    walkers.forEach((w) => { const [x, y] = onRim(toMap(w.m.position.x, w.m.position.z)); g.beginPath(); g.arc(x, y, 6, 0, Math.PI * 2); g.fill(); });
    g.fillStyle = '#f2efe8';
    g.beginPath(); g.moveTo(half, half - 14); g.lineTo(half + 10, half + 10); g.lineTo(half, half + 4); g.lineTo(half - 10, half + 10); g.closePath(); g.fill();
  }

  // ------------------------------------------------------------------ the loop
  let last = performance.now();
  let titleSpin = 0;
  let fpsNow = 0, fpsN = 0, fpsT = 0;
  function frame(now) {
    // Up to a tenth of a second a frame: an older phone at 12 frames a second still plays in real
    // time, and a long stall (the app in the background) doesn't jump the day forward.
    const dt = Math.min(0.1, (now - last) / 1000);
    fpsN++; fpsT += now - last;
    if (fpsT > 1000) { fpsNow = fpsN; fpsN = 0; fpsT = 0; }
    last = now;
    const playing = gs.phase !== 'title' && gs.phase !== 'end';
    if (playing && !modalOpen) {
      updatePlayer(dt);
      updateAction(dt);
      updateWorld(dt);
      simulate(dt * timeScale(), awayNow());
      markers.forEach((m) => {
        const on = m.active();
        m.group.visible = on;
        if (!on) return;
        m.group.position.y = groundY(m.x, m.z);
        const frac = m === nearMarker && input.action && lastCtxKind === 'marker' ? clamp(holdT / holdOf(m), 0, 1) : 0;
        // steady while it fills, so the countdown sits exactly on the ring
        m.ring.scale.setScalar(frac > 0 ? 1 : 1 + Math.sin(now / 250) * 0.08);
        m.beam.visible = frac === 0;
        ringProgress(m, frac);
      });
    }
    if (cellsDirty) paintCells();
    surfWait -= dt;
    if (surfDirty && surfWait <= 0) { surfWait = 0.1; refreshSurface(); }
    if (playing && !modalOpen) updateEffects(dt);
    updateParticles(dt);
    updateSound(dt, modalOpen || !playing);
    jib.rotation.y = Math.sin(now / 21000) * 1.4 + 0.6;
    pUni.scale.value = renderer.domElement.height / (2 * Math.tan((camera.fov * Math.PI) / 360));
    light(gs.t);
    if (gs.phase === 'title') {
      titleSpin += dt * 0.12;
      const R = Math.max(16, Math.hypot(site.box.x1 - site.box.x0, site.box.z1 - site.box.z0) * 0.95);
      camera.position.set(site.mid.x + Math.cos(titleSpin) * R, 6 + R * 0.25, site.mid.z + Math.sin(titleSpin) * R * 0.8);
      camera.lookAt(site.mid.x, 0, site.mid.z);
    } else if (gs.phase !== 'end') {
      placeCamera(dt);
      updateHUD(dt);
    }
    renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }

  // ------------------------------------------------------------------ title
  function showTitle() {
    newCast();
    day = newDay();
    gs = freshState();
    buildSite();
    freshSurface();
    cellsDirty = true;
    const season = { winter: 'Winter', spring: 'Spring', summer: 'Summer', autumn: 'Autumn' }[day.season];
    $('#dayCard').innerHTML = [
      [`${season}`, `${day.baseTemp.toFixed(0)} °C`],
      ['Humidity', `${day.rh}%`],
      ['Wind', `${day.wind} m/s`],
      ['Slab', `${day.area} m² · ${day.thick} mm`],
      ['Concrete', `${volumeNeeded().toFixed(1)} m³${day.area > 50 ? ' · ride-on' : ''}`],
      ['Pump', day.boom ? 'Boom pump' : 'Line pump'],
    ].map(([k, v]) => `<div>${k}<b>${v}</b></div>`).join('');
    const best = Number(store('pourday.best') || 0);
    $('#bestLine').textContent = best ? `Best shift so far: ${best} points` : 'Pump at seven. Mixer at half past. Probably.';
    $('#title').hidden = false;
    $('#end').hidden = true;
    $('#hud').hidden = true;
  }
  function resetWorld() {
    markers.slice().forEach(removeMarker);
    walkers.slice().forEach((w) => scene.remove(w.m));
    walkers.length = 0;
    drives.length = 0;
    pipeGroup.clear();
    pipeMeshes.length = 0;
    pump.visible = false; mixer.visible = false; pumpGuy.visible = false; pile.visible = false; tripod.visible = false;
    freshSurface();
    tarp.visible = false;
    kneel = 0;
    // yesterday's tools back in the van
    Object.values(lying).forEach((g) => { g.visible = false; });
    Object.values(machines).forEach((m) => { m.group.visible = false; m.spin = 0; });
    floatTool.visible = floatPole.visible = endHose.visible = stream.visible = false;
    workTrowel.visible = workArm.visible = false;
    boomAim.x = NaN;
    mixGuy.visible = false;
    pv.forEach((p) => { p.life = 0; });
    pSize.fill(0);
    rainUntil = 0;
    cupT = 0;
    player.fall = 0;
  }
  window.addEventListener('pointerdown', audioStart, true);
  const soundLabel = () => { $('#btnSound').textContent = soundOn ? 'Sound: on' : 'Sound: off'; };
  soundLabel();
  $('#btnSound').addEventListener('click', () => { audioStart(); setSound(!soundOn); soundLabel(); sfx('chime'); });
  const voicesLabel = () => { $('#btnVoices').textContent = voicesOn ? 'Voices: on' : 'Voices: off'; };
  voicesLabel();
  $('#btnVoices').addEventListener('click', () => { setVoices(!voicesOn); voicesLabel(); if (voicesOn) say('"Voices on. God help us."', 'foreman'); });
  const musicLabel = () => { $('#btnMusic').textContent = musicOn ? 'Music: on' : 'Music: off'; };
  musicLabel();
  $('#btnMusic').addEventListener('click', () => { audioStart(); setMusic(!musicOn); musicLabel(); });
  $('#btnStart').addEventListener('click', () => {
    audioStart();
    resetWorld();
    gs = freshState();
    // the same day as on the card, with its jobs laid out fresh
    buildSite();
    cellsDirty = true;
    buildLateMarkers();
    startDay();
  });
  $('#btnReroll').addEventListener('click', () => showTitle());

  // Inside MixMaster the app hands the page a way out; in a plain browser there isn't one.
  const bridge = window.MixMaster && typeof window.MixMaster.quit === 'function' ? window.MixMaster : null;
  const quit = () => { if (bridge) bridge.quit(); };
  $('#btnQuitTitle').hidden = !bridge;
  $('#btnQuitEnd').hidden = !bridge;
  $('#btnQuitTitle').addEventListener('click', quit);
  $('#btnQuitEnd').addEventListener('click', quit);

  const PHASE_NAMES = { morning: 'the morning', prep: 'prep', pipes: 'the pipes', pour: 'the pour', wash: 'washing up', cure: 'curing' };
  function howToPlay() {
    modal({
      who: 'How to play', title: 'The short version.',
      text: 'Left thumb walks, right thumb looks around.\n\nHold the big button to work: on whatever glows orange nearby (the ring fills as you hold), or on the slab with what is in your hands. Slide your thumb on the button while you hold it and you look round — that is how you steer the float and the trowels.\n\nNothing is in your pocket. The tools wait on the blue tarp by the van, the trowels next to it; walk up, look at one and press Pick up (the button above Wait; it lights up orange). The same button puts it down where you stand. A job that needs a tool says which.\n\nThe trowels come with pans on. Fit blades (the button next to Put down) for the blade pass, and back again if it needs more flattening. Orange squares are the ones this pass has not been over. Pans from 25% — earlier and they dig in — blades from 55%. The small trowel does straight edges; corners and pipe collars are hand-trowel work.\n\nLaser: green on height, red high, blue low; the receiver beeps fast high, slow low, steady on height. Pack it into the van after the pour.\n\nLook at somebody heading for your slab and tap to shout. Dogs too. Home at 95%.',
      choices: [{ label: 'Back to work', primary: true }],
    });
  }
  function pauseMenu() {
    if (modalOpen || gs.phase === 'title' || gs.phase === 'end') return;
    hush();
    const choices = [
      { label: 'Resume', primary: true },
      { label: 'How to play', fn: () => howToPlay() },
      { label: 'Start a new day', fn: () => modal({
        who: 'New day', title: 'Walk off this one?', text: 'This slab stays how it is. The foreman will hear about it.',
        choices: [{ label: 'New day', danger: true, fn: () => { resetWorld(); showTitle(); } }, { label: 'Keep going', primary: true }],
      }) },
    ];
    choices.splice(2, 0, { label: soundOn ? 'Sound: on — switch off' : 'Sound: off — switch on', fn: () => { setSound(!soundOn); soundLabel(); } },
      { label: musicOn ? 'Music: on — switch off' : 'Music: off — switch on', fn: () => { setMusic(!musicOn); musicLabel(); } },
      { label: voicesOn ? 'Voices: on — switch off' : 'Voices: off — switch on', fn: () => { setVoices(!voicesOn); voicesLabel(); } });
    if (bridge) choices.push({ label: 'Back to MixMaster', danger: true, fn: quit });
    modal({
      who: 'Paused', title: 'Take five.',
      text: `${clock(gs.t)}, ${PHASE_NAMES[gs.phase] || 'on site'}.` + (gs.poured ? ` Hardness ${Math.floor(gs.H)}%.` : '') + ' Time stops while you\'re here. The concrete will pretend it did too.',
      choices,
    });
  }
  $('#btnMenu').addEventListener('click', () => pauseMenu());
  /** The phone's back: pauses a shift under way, leaves from the title and end screens. */
  window.pdBack = () => {
    if (gs.phase === 'title' || gs.phase === 'end') return 'quit';
    if (!modalOpen) pauseMenu();
    return 'paused';
  };
  $('#btnAgain').addEventListener('click', () => { resetWorld(); showTitle(); });

  if (document.fonts && document.fonts.load) {
    Promise.all([document.fonts.load('800 20px Manrope'), document.fonts.load('700 20px Manrope')]).catch(() => {}).then(() => {});
  }
  showTitle();
  requestAnimationFrame(frame);

  if (DEBUG) {
    window.__pd = {
      get gs() { return gs; }, get day() { return day; },
      get near() { return nearMarker && nearMarker.id; }, get target() { return target && target.idx; }, get input() { return input; },
      get fps() { return fpsNow; }, context: () => context(),
      markers, player, simulate, finishPour, tryGoHome, completePass, nuisance, fall,
      doMarker(id) { const m = markers.find((x) => x.id === id && x.active()); if (m) { m.done(); return true; } return false; },
      fillLevel() { gs.cells.forEach((c) => { c.fill = day.thick + rnd(-3, 3); }); cellsDirty = true; },
      setTool(t) { if (held()) putDown(true); if (t !== 'hands') { if (!gs.tools[t]) gs.tools[t] = { in: 'ground', x: player.x, z: player.z, yaw: 0 }; gs.tools[t].in = 'hand'; } gs.tool = t; },
      closeModal() { const b = document.querySelector('#mChoices button'); if (b) b.click(); },
      modalOpen() { return !!modalOpen; },
      pourSome(sec) { for (let k = 0; k < sec * 30; k++) { target = gs.cells[k % gs.cells.length]; target._hx = gx(target.i) + 0.5; target._hz = gz(target.j) + 0.5; pourTick(1 / 30); } },
      // the machine run along every row at a walking pace (m/s), 20 steps a second
      sweep(fit, speed, machine) {
        const id = machine || 'trowelBig';
        if (!gs.tools[id]) gs.tools[id] = { in: 'ground', x: 0, z: 0, yaw: 0 };
        if (held() && held() !== id) putDown(true);
        gs.tool = id; gs.tools[id].in = 'hand'; gs.fit[id] = fit === 'blades' ? 'blades' : 'pans';
        for (let j = 0; j < NZ; j++) for (let x = SLAB.x0 + 0.3; x < SLAB.x1 - 0.3; x += (speed || 1) * 0.05) { gs.tools[id].x = x; gs.tools[id].z = SLAB.z0 + j + 0.5; gs.tools[id].yaw = 0; trowelTick(0.05); }
      },
      pickUp: (id) => pickUp(id), putDown: () => putDown(true), get tool() { return gs.tool; }, get nearTool() { return nearTool; }, site,
      get holdT() { return holdT; }, get info() { return { calls: renderer.info.render.calls, tris: renderer.info.render.triangles, geos: renderer.info.memory.geometries, meshes: (() => { let n = 0; scene.traverseVisible((o) => { if (o.isMesh) n++; }); return n; })() }; }, get lastCtx() { return lastCtxKind; },
      walkers, mixer, swapFit: () => swapFit(), discs: () => discs(), machines,
      spawnCross() { const side = 'w'; return spawnWalker('person', wander([farPoint(side), edgePoint(side)], 0.8), 1.4, { who: L.cross[0], from: side, onArrive: askToCross }); },
      spawnDog() { const side = 'w'; return spawnWalker('dog', [farPoint(side), edgePoint(side)], 3.2, { from: side, state: 'approach', onArrive: (w) => { w.state = 'eyeing'; w.pause = 30; } }); },
      reroll() { showTitle(); return day.area; },
      setDay(o) { Object.assign(day, o); }, get boomTip() { return boomTip.toArray().map((v) => +v.toFixed(2)); },
      rms: () => rms(), stamp: (k, x, z) => stamp(k, x, z, 0), marks: () => gs.cells.reduce((n, c) => n + c.marks.length, 0),
      sayTest: (t, w) => say(t, w), L, get cast() { return cast; },
      packUp: () => packUp(), get packing() { return gs.packing; }, tooLate: () => tooLate(),
      visit: (k, away) => ({ ufo: ufoVisit, ball: ballVisit, cat: catVisit, drone: droneVisit, bag: bagVisit })[k](!!away),
      odd, walkers, slabReport: () => slabReport(), glyphs: () => gs.cells.reduce((n, c) => n + c.marks.filter((m) => m.kind === 'glyph').length, 0),
      get said() { return saidLog; },
      get audio() { return ac && ac.state; }, get sound() { return soundOn; }, get scene() { return scene; }, get camera() { return camera; },
    };
  }
})();
