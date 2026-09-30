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
  // Inside an app — MixMaster, or the Pour Day app on its own — the app hands the page a way out,
  // voices and co-workers' phones; in a plain browser, none of those.
  const appBridge = window.PourDayApp && typeof window.PourDayApp.quit === 'function' ? window.PourDayApp : null;
  const appName = (() => { try { return appBridge && appBridge.appName ? String(appBridge.appName()) : ''; } catch (e) { return ''; } })();
  // Pour Day on its own just closes; inside MixMaster the way out goes back to it.
  // The company's logo is on the backs of people playing in MixMaster, the company's app; Pour Day
  // on its own is for people outside it, and nobody there wears it.
  const inMixMaster = appName === 'MixMaster';
  const QUIT_LABEL = appName && appName !== 'Pour Day' ? `Back to ${appName}` : 'Quit';
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
    van: P(-26, 9), vanDoor: P(-23.4, 8.0), vanSeat: P(-25, 7), vanCorner: P(-23, 10),
    ibc: P(-21, -9), ibcFront: P(-21, -7.6),
    kiosk: P(-38, -24), kioskFront: P(-35.4, -22.2),
    tripod: P(-8.6, 0.6),
    tarp: P(-19.5, 5.4),
    pump: P(21.5, -3), pumpOut: P(17.9, -2.6),
    mixer: P(23, 4.5),
    pile: P(16.2, 1.6),
    loo: P(-31, -15), looFront: P(-31, -13.7),
    office: P(-34, 13),
    vanSide: P(-22, 8),
  };
  const PIPE_ROUTE = [P(16.3, -2.3), P(14.1, -2.0), P(11.9, -1.6), P(9.7, -1.1), P(7.7, -0.6), P(6.4, -0.2)];
  // as many pipes as it takes from wherever the pump parked today: three on a good day, nine on a bad one
  let PIPE_N = 6;
  const pipeEnd = () => PIPE_ROUTE[PIPE_N - 1];
  const TRUCK_M3 = 8;                 // a full mixer; a small pour comes as a part load

  // ------------------------------------------------------------------ what people say
  const L = {
    alarm: [
      '04:45. The alarm. Your back already knows what day it is.',
      '04:45. The alarm goes off. So does your knee, in sympathy.',
      '04:45. It is dark, it is cold, and somewhere a concrete plant is warming up just for you.',
      '04:45. The alarm. You lie there and briefly consider faking your own death. Too much paperwork.',
      '04:45. Your phone says "Good morning!". Your phone has never poured a slab.',
      '04:45. You dreamt you were an accountant. You wake up crying, and not from relief.',
      '04:45. Your knees vote to stay in bed. It is not a democracy.',
      '04:45. The cat watches you get dressed with open contempt.',
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
      'The site is so quiet you can hear your pension shrinking.',
      'A crow on the fence watches you unlock the van. It has seen this before. It knows how it ends.',
      'Frost on the formwork. Frost on the van. Frost, somehow, in your coffee.',
      'The portaloo door swings in the wind like it\'s waving. Or warning.',
      'First one here again. Nobody gives medals for this. You checked.',
    ],
    pumpArrive: [
      '"Morning. Where do you want the pipes? Don\'t say \'in the van\'."',
      '"I\'ve been driving since four. If this pour blocks, I\'m blaming you personally."',
      '"Nice base. Shame about what\'s going to happen to it."',
      '"Morning. Is that the base? Brave."',
      '"I\'ve got three more pours after this, so if you could finish yesterday, that\'d be great."',
      '"Last guy I pumped for cried. Happy tears. Mostly."',
      '"Don\'t touch the remote. The last person who touched the remote is now a garden feature."',
      '"Nice day for it. Well. For it. Not for you."',
      '"I brought the long pipes today. They\'re heavier. You\'re welcome."',
    ],
    pumpLate: [
      'Pump driver: "Running a bit late, the last site had a dog." ETA {eta}.',
      'Pump driver: "Coming! Just finishing my second breakfast." ETA {eta}.',
      'Pump driver: "The satnav took me to a lake. I\'m on my way." ETA {eta}.',
      'Pump driver: "Five minutes!" In pump-driver time that\'s a unit of hope, not of time. ETA {eta}.',
      'Pump driver: "Stuck behind a funeral. Very slow. Very respectful. Very long." ETA {eta}.',
      'Pump driver: "Had to go back for the pipes. And my teeth." ETA {eta}.',
    ],
    truckLate: [
      'Plant: "The truck left ten minutes ago." It did not. ETA {eta}.',
      'Plant: "The driver is on his way, he just had to finish his sausage." ETA {eta}.',
      'Plant: "Traffic." Plant always says traffic. ETA {eta}.',
      'Plant: "He\'s five minutes away." He is in a lay-by eating a pasty. ETA {eta}.',
      'Plant: "Your order? Oh, THAT order." ETA {eta}.',
      'Plant: "The driver\'s new. He\'s found the site twice. Neither was yours." ETA {eta}.',
    ],
    truckDriver: [
      '"Plant says S3. The plant also says it loves you."',
      '"Where do you want it? Don\'t say \'on the slab\', everyone says that."',
      '"Quick one today? I have a funeral at two. Mine, if I\'m late."',
      '"Nine cubes of happiness. Well, eight and a half. The plant rounds up."',
      '"Where\'s the boss? Oh, you\'re the boss? Condolences."',
      '"Third coffee, fourth site. One of us is going to crack today and it\'s going to be the concrete."',
      '"It\'s been in the drum since six. It\'s had a longer morning than you."',
      '"Sign here. And here. And here: that one says it\'s your fault."',
      '"I\'ve got a joke about concrete. It takes a while to set up."',
    ],
    pipe: [
      'Clunk. The coupling bites your finger. You learn a new word.',
      'Pipe in. It weighs exactly as much as you remember, plus two kilos.',
      'You clamp it. The clamp clamps you back.',
      'Pipe down. Your back files a complaint with HR.',
      'The pipe slips. Your shin catches it. Your shin will remember this every winter.',
      'You carry the pipe like a coffin at a funeral nobody wanted to go to.',
      'Another pipe. The pile isn\'t getting smaller. The pile is breeding.',
      'Pipe clamped. Somewhere, a physiotherapist smiles and doesn\'t know why.',
    ],
    pourJokes: [
      'Pump driver, from his remote: "Faster! I get paid by the hour, but I don\'t like it."',
      'The mixer driver starts his crossword. Seven letters, "grey and heavy". He writes "MONDAYS".',
      'Somebody on the pavement films you. Wave. Now you\'re content.',
      'Your phone buzzes. It\'s the foreman asking if it\'s done yet. It is 08:15.',
      'A bird lands on the formwork, looks at the concrete, and decides against it. Smart bird.',
      'Pump driver, from his remote: "You\'re doing great! That was sarcasm. I can\'t turn it off."',
      'Mixer driver: "Every time I come here it\'s you. Do you ever go home?" You do not.',
      'Pump driver: "If it blocks now I\'m telling everyone it was you. Even if it wasn\'t. Especially if it wasn\'t."',
      'Mixer driver: "My doctor says I need more exercise. I told him I watch you work."',
      'Pump driver: "Faster! The boom\'s insurance is by the minute!"',
      'A pigeon sits on the pump and watches. Even the pigeon is on a break.',
      'Mixer driver: "Nice pour. Shame about your posture."',
      'Pump driver: "My kid wants to do this when she grows up. I\'ve started saving for her therapy."',
    ],
    falls: [
      'You sit down in it. The slab now has a perfect print of your behind.',
      'Your boot stays. You don\'t. Graceful, in a way.',
      'You slip, flail, and land on your butt. The pump driver claps.',
      'Down you go. Somewhere, a health and safety officer feels a chill.',
      'You fall with the grace of a wardrobe. The slab keeps a copy for posterity.',
      'Down you go. Your phone, loyal to the end, goes in first.',
      'You slip. For one moment you\'re flying. Then you\'re concrete.',
      'You land on your back and look at the sky. The sky has no advice either.',
      'Splat. The pump driver films it. You\'ll be in a group chat by lunch.',
      'You go down knees first. Your knees had already handed in their notice.',
    ],
    stuck: [
      'Your boot is stuck. You stand there like a garden gnome until it lets go.',
      'The concrete has your left boot. It is negotiating.',
      'Your boot is stuck. You wiggle. The concrete wiggles back. It has you.',
      'One boot out, one boot in. You hop like a flamingo in hi-vis until it lets go.',
    ],
    blocked: [
      'BANG. The line is blocked. The pump driver looks at you. You look at the pipe. Hit it.',
      'The pump groans and stops. Blocked. Somewhere a pipe needs a hammer.',
      'The line thumps and goes quiet. The pump driver says a word that stops the birds singing. Find the blockage.',
    ],
    unblocked: [
      'Clang clang clang. It coughs, spits and runs again. So does your nose.',
      'You hit the pipe. It forgives you. The pump driver does not.',
      'BANG BANG BANG. The pipe coughs up a stone the size of a potato and carries on. So do you, slightly deafer.',
    ],
    blowout: 'The formwork on the {side} opens up. Concrete is leaving the building. Fix it!',
    battery: 'The laser beeps once and dies. The spare batteries are in the van. Of course they are.',
    wash: [
      'The pump driver hoses down his pipes, the pump, your van and, briefly, you.',
      'The pump driver washes out his pipes. The run-off heads straight for the neighbour\'s rose bed.',
      'The pump driver sprays the last of the grout out of his hopper, humming. He\'s done for the day. You are not.',
      'The pump driver coils his hose and watches you with the peace of a man who is paid either way.',
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
      'Coffee. It tastes of thermos and bad decisions.',
      'You drink it too fast and burn your tongue. The first feeling in your face since five.',
      'Coffee number something. Your hands stop shaking. Then start again, faster.',
      'The coffee is lukewarm, and so is your commitment.',
      'You drink it standing up, like a horse. Horses have better pensions.',
      'One sip and you can hear colours. That\'s the good stuff.',
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
      { who: 'A pensioner with a walking stick', ask: '"I\'ve walked this way for fifty years. I\'m not stopping for a puddle."', back: '"Fifty years!" They go round, telling the whole street about it.' },
      { who: 'A dog walker with six dogs', ask: '"They go where they want, I just hold the leads. Can we cross?"', back: '"Come on, all of you." Six dogs look at the slab like it owes them money, then go round.' },
      { who: 'The client\'s mother', g: 'f', ask: '"My son is paying for this. I\'d like to stand in the middle and judge it."', back: '"I\'ll judge it from here, then. It\'s grey." She is right.' },
      { who: 'A man in a suit', g: 'm', ask: '"I\'m late for a meeting, and the meeting is on the other side of your concrete."', back: '"I\'ll tell them I was held up by a floor." He jogs the long way round, in loafers.' },
      { who: 'A cyclist', ask: '"Shortcut! Can I just ride across? Bikes are light!"', back: '"Car-brain!" They ride off round the fence, ringing the bell at you.' },
      { who: 'Door-to-door salesperson', ask: '"Have you thought about double glazing? For the floor? I\'ll come to you. Across."', back: '"I\'ll leave a leaflet." The leaflet blows onto the slab. Of course it does.' },
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
      '"Okay, okay, it\'s your precious floor." They go round. It is your precious floor.',
      '"I was only going to step on it a little bit!" They go the long way, muttering "a little bit".',
      '"Somebody got out of bed on the wrong side." You got up at 04:45. There is no right side.',
      '"Fine. I\'ll walk round your stupid rectangle." It\'s an L, actually.',
      '"I\'m leaving a review!" Of what? Of you. One star.',
    ],
    shoutHurry: [
      'They hear "run". They run. Across it. Faster. Deeper.',
      '"I\'m nearly over!" They are not nearly over.',
      'They speed up, apologising with every single footprint.',
      'They run. On fresh concrete. Like a cartoon. Every step a crater.',
      '"Nearly there!" They were nearly there. Now they\'re nearly there with deeper holes.',
      'They tiptoe faster. Tiptoes go deeper. Physics is not on your side today.',
    ],
    shoutFreeze: [
      'They freeze mid-step like a gnome. Then they carry on, as if the shout was weather.',
      'They stop, look at you, look at their shoe, and keep going. Slower. Deeper.',
      'They freeze, then back out slowly, stepping in their own prints. It does not help.',
      'They stop dead in the middle and ask "which way is out?". All ways are out. All of them.',
    ],
    driverTalk: [
      'Pump driver: "You know what the difference is between a pump driver and a concrete finisher? I go home at eleven."',
      'Pump driver: "Stiffer mix next time and I\'m charging for the swearing."',
      'Pump driver: "My wife thinks I\'m a pilot. Don\'t tell her."',
      'Pump driver: "I once pumped a whole pool through a keyhole. Different day. Different keyhole."',
      'Pump driver: "Is that your float? Looks like it\'s seen things."',
      'Pump driver: "I\'ve seen worse finishers. Not today. But I have."',
      'Pump driver: "Thirty years on the pump. My ears ring in the key of C."',
      'Pump driver: "My back\'s fine. The rest of me is on a waiting list."',
      'Pump driver: "Retire? I\'ll retire when the pump does. The pump\'s younger than me."',
      'Pump driver: "People ask if I like my job. I tell them I love the diesel. It\'s not a lie."',
      'Pump driver: "My doctor told me to reduce stress, so I stopped answering the plant."',
      'Pump driver: "Seen the forecast? Me neither. I just assume rain and disappointment."',
      'Pump driver: "I\'ve got a joke about the plant. It\'ll be here in forty minutes. Maybe."',
    ],
    mixTalk: [
      'Mixer driver: "Plant says it\'s S3. Plant also says it\'s Tuesday."',
      'Mixer driver: "Take your time. I\'m paid by the hour. You\'re paying, by the hour."',
      'Mixer driver: "If you want it wetter, I\'ve got a hose. If you want it drier, I\'ve got a hose and regrets."',
      'Mixer driver: "Thirty years on the drum. Still dizzy."',
      'Mixer driver: "The drum spins, I spin. My inner ear retired in 2009."',
      'Mixer driver: "Plant says it\'s the good stuff today. Plant says that about everything. Plant said that about my ex."',
      'Mixer driver: "My wife asked what I do all day. I said I wait for people like you. She said: same."',
      'Mixer driver: "This truck is older than my marriage and it runs better."',
      'Mixer driver: "Don\'t mind me. I\'m just here to be paid for standing."',
      'Mixer driver: "Don\'t worry, I\'ve got all day. That\'s not a kindness, it\'s a threat."',
    ],
    crossAnyway: [
      'They walk across anyway. Confident stride, size 45. Perfect prints.',
      'They nod, agree with everything you said, and step straight on it.',
      '"I\'m light!" They are not light.',
      '"I\'ll walk on the lines." There are no lines. There are only footprints now.',
      'They take their shoes off first, as a courtesy. Barefoot prints. Toes and everything.',
      'They cross while holding eye contact the whole way. A power move. Ankle-deep.',
      '"Don\'t worry, I\'m a tiptoer." Tiptoe prints go deeper. They know that now.',
      'They cross, take a selfie in the middle, then cross back for a better angle.',
    ],
    crossAway: [
      'While you were away someone crossed the slab. Size 45, confident stride. Probably the electrician.',
      'You find footprints across the slab. They stop in the middle, turn around, and go back. Why.',
      'Someone crossed while you were gone. There\'s a coffee cup lid in the middle as a signature.',
      'While you were away someone walked across, stopped in the middle and, from the prints, did a little dance.',
      'While you were away someone pushed a bicycle across the slab. The tyre track goes right through the middle, like a signature.',
      'Prints across the slab and a lost glove in the middle. You will never find the owner. You will think about them forever.',
      'Someone crossed while you were gone and wrote SORRY at the far edge with a stick. Very polite. Very deep.',
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
      'The dog leaves, but looks back once, just to make you feel bad. It works.',
      'The dog sighs like a teenager and walks off. It will be back. They always come back.',
      'The dog goes. The owner appears, shouts "He\'s friendly!", and doesn\'t apologise. The dog was never the problem.',
    ],
    dogGame: [
      'The dog hears "PLAY WITH ME". Laps of honour on the slab.',
      'The dog thinks shouting is a game. It is winning.',
      'The dog does a victory lap. Then a lap of honour. Then a lap just for you.',
      'The dog finds the wettest corner and rolls in it. Joy has a shape, and the shape is in your slab.',
      'You shout. The dog barks back. You are now in an argument, and you are losing.',
    ],
    dogSausage: 'The dog catches the sausage mid-air and leaves with it. Best trade of the day.',
    bird: 'A seagull landed on the slab, walked three steps, and left you a little something extra.',
    foreman: [
      '"Is it hard yet? The client wants to drive a forklift on it at two."',
      '"Quick one — can we do the second floor tomorrow? There is no second floor. Doesn\'t matter."',
      '"Just checking you\'re not on your phone." You are, because he called.',
      '"The client wants it a bit more... greyer? I said yes. Make it greyer."',
      '"Are you on site? I can\'t see you on the camera. There is no camera. Or is there."',
      '"Good news: the client loves it. Bad news: they want another one. Worse news: tomorrow."',
      '"Just so you know, the budget\'s gone. Don\'t ask where. Keep pouring."',
      '"HR says I have to ask if you\'re happy at work. Are you? Great, I\'ll put yes."',
      '"How\'s the slab? No, don\'t tell me. I\'ll hear it from the client, it\'s more exciting."',
      '"Can you pick up some screws on the way home? And a new van? Joking. Screws."',
    ],
    lunch: 'KEBAB & COFFEE. The owner nods at you like he knows exactly how your day is going.',
    machineDies: [
      'The {m} coughs, shudders and dies with a noise like a goat. Smoke. "No. No no no."',
      'Bang, then silence, then smoke. The {m} has retired. "Not now. Please, not now."',
      'The {m}\'s gearbox makes a sound you\'ll hear in your dreams, then nothing. "Brilliant. Just brilliant."',
    ],
    floatSnaps: [
      'The float pole folds in half, like it has had enough of you too.',
      'Crack. The float pole snaps. You\'re holding a stick; the float lies in the concrete like a flag of surrender.',
    ],
    hammerBreaks: [
      'The hammer head flies off and lands in the next postcode.',
      'You swing. The head stays on the stake. The handle comes back to you, alone.',
    ],
    cutterBreaks: [
      'The cutter handle snaps. The bar wins. The bar always wins in the end.',
      'Crack: the cutter gives up before the rebar does.',
    ],
    washed: [
      'Clean enough to eat off. Don\'t.',
      'Water everywhere, mostly on you.',
      'Shiny. For about ten minutes.',
      'The water tank is now five per cent concrete.',
    ],
    leftTools: [
      '"You left {t} on site. Do you know what happens to tools left on site? They go to live with the scrap man. He\'s very happy. You\'re not."',
      '"Did you pack the van with your eyes shut? {t}. Still out there. Waving at the neighbours."',
      '"I\'m looking at a photo of {t}, lying on site in the dark. The neighbour sent it. He wants to know if he can keep them."',
    ],
    dirtyTools: [
      '"And {d}: covered in concrete. That\'s not a tool any more, that\'s a sculpture. You\'re paying for the sculpture."',
      '"{d} came back grey. The apprentice will chisel it off. The apprentice is billing you."',
      '"{d}. Filthy. Did you wash them in the slab?"',
    ],
    looOne: [
      'A long piss. The portaloo smells like a festival that went wrong.',
      'You piss for a full minute. The door doesn\'t lock. Somebody tries it. You hold it shut with one foot.',
      'A piss at last. No paper, one spider, and a phone number on the wall you do not call.',
    ],
    looTwo: [
      'A shit of historic proportions. You sit in a plastic box and think about your choices. Twelve minutes, all of them yours.',
      'The kebab comes back to visit, and it brings friends. You come out a changed man.',
      'You take a shit. There\'s no paper. There\'s a delivery note. You use the delivery note.',
    ],
    weeSoon: [
      '"I need a piss."',
      '"Should not have had that third coffee."',
      '"Right. Toilet. Soon. Very soon."',
      '"My bladder is filing a formal complaint."',
    ],
    pooSoon: [
      '"Oh no. The kebab."',
      '"That is not a noise a stomach should make."',
      '"I need a shit. Now. Walk, don\'t run. Running is dangerous."',
      '"Code brown. This is not a drill."',
    ],
    // holding it in: what you mutter, and the cramps
    pissHold: [
      '"Think of deserts. Dry, dusty deserts."',
      '"Hold it. Hold it. You\'re a professional."',
      '"Don\'t look at the water tank. Don\'t look at the hose."',
      '"I can hear running water. Why can I always hear running water."',
      '"Legs crossed. Legs crossed and dignity."',
    ],
    cramp: [
      '"Nnnngh."',
      '"Clench. Clench like your job depends on it. It does."',
      '"Not here. Not now. Not on the slab."',
      '"Whatever that kebab was, it wants out, and it wants out NOW."',
    ],
    pissedSelf: [
      'Too late. It\'s warm, and then it isn\'t. A dark patch spreads down one leg, in front of everybody.',
      'You couldn\'t hold it. Your trousers are now two colours. The pump driver saw. The pump driver is filming.',
    ],
    shatSelf: [
      'You didn\'t make it. Nobody who was there will ever speak of it. Everybody who wasn\'t will hear about it.',
      'The kebab wins. Your trousers lose. The flies arrive before you\'ve finished swearing.',
    ],
    mePissed: ['"Nobody saw. Nobody saw. Everybody saw."', '"It\'s sweat. It\'s very specific sweat."'],
    meShat: ['"I\'m going to need the water tank. And a new name."', '"Walk normally. Walk NORMALLY."'],
    // everybody else, getting a whiff of you
    smellReact: [
      '"Something died. Something died IN YOU."',
      '"Is that you? Mate. MATE."',
      '"Stand downwind. Stand in another town."',
      '"I\'ve smelt a lot of sites. That\'s a new one."',
      '"The flies have found you. They\'re calling their friends."',
    ],
    wetReact: [
      '"Spilled your coffee, did you? Down there? Right."',
      '"Mate, your trousers. No, the front. Yeah."',
      '"We\'ve all been there. Not on site. Not in front of the pump driver."',
    ],
    rebarUp: [
      'A corner of the mesh comes up out of the concrete like a hand from a grave. Push it down before it sets!',
      'The mesh pops up through the pour. It wants to see the sky. It can\'t. Push it down!',
      'A bar end rises out of the grey, slowly, like a periscope. Down with it, now!',
    ],
    pumpHelpStart: [
      '"Give me that. I\'ll show you how it\'s done."',
      '"You\'re too slow. Move. Watch a professional."',
      '"The truck\'s been waiting so long it\'s growing moss. Hand it over."',
    ],
    pumpHelpEnd: [
      '"There. You\'re welcome. Level that."',
      '"Done. Well. More done. Your turn."',
      '"Faster, see? Some of it\'s a bit high. That\'s a you problem."',
    ],
    shovelLines: [
      'You shovel the hill into the hole. Somewhere, a physiotherapist gets a new car.',
      'Shovel, shovel, shovel. Your back writes a strongly worded letter.',
    ],
    idle: {
      pump: [
        '"Oi! Are you working or modelling? The pump costs the same either way."',
        '"You\'ve stood there so long I took you for a survey peg."',
        '"Move! I\'m paid by the hour, and so, apparently, are you."',
        '"Is this a sit-in? What are we protesting? Tell me and I\'ll bring a chair."',
        '"Standing still is for statues and concrete. You\'re neither. Yet."',
        '"Every second you stand there, a bag of cement somewhere loses the will to live."',
        '"If you\'re waiting for inspiration, it went home at seven."',
        '"Hello? Earth to the man in the boots! It won\'t level itself!"',
        '"I\'ve had pipes blocked that moved more than you."',
      ],
      truck: [
        '"Mate! My drum is turning and you\'re not. One of us is working."',
        '"Standing still costs eighty euros an hour. In front of my truck it costs more."',
        '"I\'ve got three more sites today and a wife with a stopwatch. Move!"',
        '"You look like a man waiting for a bus. The bus is me. Get on with it."',
        '"Oi, sleeping beauty! This load goes off in an hour, with or without you."',
        '"Stand there much longer and I\'m charging you rent."',
        '"Forgotten what you came for? It\'s concrete. It\'s grey. It\'s in my truck."',
        '"My grandmother moves faster, and she\'s been dead six years."',
      ],
      again: [
        '"STILL? I\'ve seen more life in a cured slab!"',
        '"Right, I\'m filming you now. Your manager gets the video."',
        '"Do you need a hand, a coffee or a doctor? Pick one and MOVE."',
        '"I\'m starting to think you\'re part of the formwork."',
        '"That\'s twice. Three times and I pour it on you."',
        '"Your manager just rang to ask if you\'re alive. I said I couldn\'t tell."',
        '"I\'ll put it on your gravestone: he stood there. For ages."',
        '"Are you stuck? Blink twice if the rebar has you."',
      ],
      hose: [
        '"Move the hose! You\'re building a pyramid, not a floor!"',
        '"Ten seconds on one spot? That\'s not a slab, that\'s a monument."',
        '"Spread it about! The corners want some too!"',
        '"You\'re pouring a mountain. The client ordered a floor."',
      ],
    },
    mateTalk: [
      '"Who packed the van? I want to shake their hand. Round the neck."',
      '"My back just made a noise like a dropped pallet."',
      '"If I fall in, tell my wife the slab was level."',
      '"I\'m not saying I\'m tired, but I just tried to float the dog."',
      '"Is it lunch? It feels like lunch. It\'s eight in the morning."',
      '"I\'ve got concrete in places concrete shouldn\'t know about."',
      '"The manager says we\'re a family. Families don\'t dock your pay for a footprint."',
      '"Ten more years of this and I can afford a house. Made of concrete. Poured by me."',
      '"Did you hear that? That was my knee. It\'s handing in its notice."',
      '"Don\'t tell anyone, but I actually like this bit."',
      '"You\'re doing it wrong. I don\'t know how it\'s done, but you\'re doing it wrong."',
    ],
    meUnspill: [
      '"Back where you belong. Mostly."',
      '"That\'s most of it. The gravel can keep the rest as a souvenir."',
      '"Nobody saw that. The heap never happened."',
      '"Recycling. The manager loves recycling. He\'ll never know."',
      '"Shovelled back. My back will send the invoice later."',
    ],
    bootsHeavy: [
      'Your boots weigh a kilo each now. Concrete ones. Very Italian.',
      'Every step brings half the slab with it. The slab would like it back.',
      'Your boots are grey to the laces. Walk off the slab and you\'ll print a map of your day across the gravel.',
      'Heavy boots. You walk like a deep-sea diver on his day off.',
    ],
    bootsWashed: [
      'Boots hosed. Black again, for about four minutes.',
      'You hose your boots and most of your socks. Clean-ish.',
      'Boots washed. The water tank has seen things today.',
    ],
    myVoiceTry: [
      'Right. Concrete. Let\'s get it over with.',
      'Morning. Where\'s the coffee. Where\'s the pump.',
      'That\'s me. I sound like I need a holiday.',
      'Another day, another slab, same knees.',
      'Who ordered the stiff mix? Was it me? It was me.',
      'Twenty years on the tools and this is what I sound like.',
      'If the manager rings, I\'m under the slab.',
    ],
    flip: {
      person: [
        ['flip1', 'They flip you back. Stalemate.'],
        ['clutch', '"Charming!" They clutch their pearls. They don\'t have pearls. They clutch something.'],
        ['phone', '"I\'m telling your boss!" Your boss would be proud.'],
        ['hurry', 'They gasp, then speed up. It works better than shouting.'],
        ['flip1', '"Same to you, pal!" Fair.'],
        ['phone', 'They film you doing it. You\'ll be on the local Facebook group by lunch.'],
        ['flip2', 'Both hands, both fingers, no hesitation. "And your concrete!" A professional.'],
        ['flip1', 'They flip you back without looking up from their phone. Multitasking.'],
        ['flip2', 'They put the shopping down to do it with both hands. "There. Now we\'re even."'],
        ['clutch', '"In front of the children?" There are no children. They look round for some.'],
        ['flip1', '"Right back at you, concrete boy." They hold it up until they\'re round the corner.'],
        ['flip2', 'Two fingers back, and a little bow. "Have a hard day. Like your slab."'],
      ],
      pump: [
        ['flip2', 'The pump driver lets go of the remote and flips you back with both hands. "Two for one, son. Pump\'s special."'],
        ['flip2', 'Both fingers from the pump driver, the remote swinging off his belly. "Careful. I\'ve got the pump and a long memory."'],
        ['flip2', 'Both hands, both fingers, not a flicker on his face. "Thirty years I\'ve been doing this. The pumping, too."'],
        ['flip1', 'He flips you back with one hand and thumbs the remote with the other. The hose gives a cough. "Oops."'],
        ['flip2', 'Both fingers up, and a smile. "Keep pointing. I\'m paid by the hour."'],
        ['flip1', 'One finger, held up for a long time. "I can do this all day. I DO do this all day."'],
      ],
      mixer: [
        ['flip1', 'The mixer driver gives you a finger and reaches into the cab with the other hand. Two honks. "Same to you!"'],
        ['flip2', 'The mixer driver flips you back with both hands, then nods at the drum. "Nine cubes of that, and you\'re the one with the attitude?"'],
        ['flip1', '"Yeah, yeah." One finger, not even looking. He\'s seen a lot of slabs.'],
        ['flip2', 'Both fingers, then he taps his watch. "Every minute you wave at me is on your invoice."'],
      ],
      nothing: [
        'You flip off the day in general. It helps a bit.',
        'You flip off nobody in particular. A crow takes it personally.',
        'You flip off thin air. The air takes it well.',
      ],
      dog: [
        'The dog doesn\'t understand. It wags harder.',
        'The dog tilts its head. You feel bad now.',
        'The dog sits and offers you a paw. You are the worst person on this site.',
      ],
      cat: [
        'The cat looks at your finger, then at you, then away. You have been judged and found boring.',
        'The cat blinks slowly. In cat, that\'s worse than what you just did.',
        'The cat yawns at you. Every tooth. On purpose.',
        'The cat turns round and shows you its behind. Stalemate.',
      ],
      things: {
        van: ['You flip off the van. The van has seen worse. It was there for most of it.', 'You flip off your own reflection in the van window. It flips you back. Fair.', 'You flip off the van. It\'s the only one driving you home, so maybe apologise.'],
        pump: ['You flip off the pump. The pump doesn\'t care. It\'s a pump.', 'You flip off the pump. It thumps on, a heart that bills by the hour.'],
        mixer: ['You flip off the mixer truck. The drum keeps turning. It\'s seen your slab.', 'You flip off nine cubes of concrete. They go off either way.'],
        laser: ['You flip off the laser. It beeps. You decide that was an apology.', 'You flip off the laser. It stays perfectly level about it. Smug.'],
        loo: ['You flip off the portable toilet. Honestly, it had it coming.', 'You flip off the toilet. It answers with the smell.'],
        kiosk: ['You flip off the kebab stand. You\'ll be back at lunch and you both know it.', 'You flip off the kebab. The kebab turns slowly away from you.'],
        tank: ['You flip off the water tank. It gurgles. That\'s a no.', 'You flip off the water tank. It\'s the only clean thing on site and now it\'s been insulted.'],
        pipes: ['You flip off the pipes. All of them. They\'re still heavy.', 'You flip off the pipe pile. It rolls one at you. Probably the wind.'],
        crane: ['You flip off the crane on the next site. The driver is too high up to care. Or see.'],
        hall: ['You flip off the wall. The wall stays. Walls always win.', 'You flip off the building. It echoes. So does your career.'],
        tool: ['You flip off the {t}. It isn\'t going to wash itself either.', 'You flip off the {t}. It had it coming. Tools always do.', 'You flip off the {t}. It lies there, unimpressed. Like the manager.'],
        slab: ['You flip off the slab. It doesn\'t care. It\'s concrete.', 'You flip off the slab. It sets a little harder, out of spite.', 'You flip off your own work. Honest, at least.'],
        gravel: ['You flip off the gravel. The gravel remains gravel.', 'You flip off the ground. It\'s been walked on all day. It\'s used to it.'],
        sky: ['You flip off the sky. It starts to drizzle. Coincidence, probably.', 'You flip off the weather. The weather has a long memory.'],
        sun: ['You flip off the sun. It carries on drying your slab too fast. On purpose now.'],
        moon: ['You flip off the moon. It has watched you work since five. It knows.'],
      },
    },
    helper: {
      names: [
        'Mart',
        'Priit',
        'Kalle',
        'Siim',
        'Rein',
        'Janek',
        'Aivar',
        'Toomas',
      ],
      sent: [
        '"It\'s a big one, so I\'m sending you {n}. Try not to break him. He\'s on loan from his mother."',
        '"I\'m sending {n} over. He\'s new. So is his hard hat. Keep it that way."',
        '"{n}\'s coming to help. Don\'t let him near the ride-on. Don\'t let him near anything, really."',
      ],
      hello: [
        '"Morning. The manager said you need help. Looking at you, he\'s right."',
        '"Hi. I\'m {n}. What do I do? Don\'t say \'anything\', I\'m bad at anything."',
      ],
      talk: [
        '"Working with you is the same as working alone, but harder."',
        '"I\'ve been here two hours and I already need a holiday."',
        '"Is it always this grey?"',
        '"My last job was in an office. I miss the chair. I miss every chair."',
        '"The manager says you\'re the best he\'s got. I\'ve seen what he\'s got."',
        '"Don\'t worry, I\'m faster than I look. I\'m also slower than I look. It\'s complicated."',
        '"Can I have a go on the ride-on? No? What if I cry?"',
        '"I think I\'ve got concrete in my soul now."',
      ],
      flipped: [
        '{n} flips you back. It\'s the most work he\'s done all day.',
        '"Nice. I\'m telling my mum."',
      ],
      oops: [
        '{n} walks straight across the slab to ask you something. "Oops." Twelve footprints of oops.',
        '{n} goes to get his phone from the other side. Across the slab. "It was quicker."',
      ],
      bye: [
        '"My shift\'s over. Good luck. You\'ll need it."',
        '"I\'m off. Same time tomorrow? Please say no."',
      ],
    },
    crewShout: [
      '"Oi! Get off my bit!"',
      '"Where\'s the float? Did you take the float?"',
      '"Are you working or modelling?"',
      '"Pour it HERE, not everywhere!"',
      '"Stop standing in my concrete!"',
      '"Coffee. Van. Now."',
      '"Is that your footprint? That\'s your footprint."',
    ],
    crewShoutOut: [
      'They pretend not to hear.',
      'They wave. It is not a friendly wave.',
      'They shout something back. The pump drowns it out. Probably for the best.',
    ],
    crewFlip: [
      'You return it. Teamwork.',
      'That\'s how you know they care.',
      'The pump driver saw. He approves.',
    ],
    vanSpill: [
      'You open the doors. Out comes {t}, the way it always does.',
      'Doors open. Out slides {t}. The van has been holding a grudge since Tuesday.',
      'You open the back and out jumps {t}. Somewhere, the man who packed the van laughs.',
      'The doors swing open and the van spits out {t}. It does this every morning.',
      'Out of the van before you touch anything: {t}. Good morning to you too.',
    ],
    vanNap: [
      'You wake up with the seatbelt printed on your face.',
      'You dreamt about troweling. Very relaxing. Then you woke up and had to do it.',
      'You nap. The radio plays the same four songs. Your dream now has a chorus.',
      'You wake up with the steering wheel printed on your forehead. You look like a target.',
      'You dreamt the slab was done. Cruelty, in dream form.',
      'You wake up and for three seconds you don\'t know where you are. Then you smell diesel.',
      'A neighbour knocks on the window to check you\'re alive. You\'re not sure either.',
      'You slept so hard you drooled on the delivery note. Now it\'s a wet delivery note.',
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
      'Tied. Your thumb now has a spiral dent in it, like a fossil.',
      'Twist, twist, snip. The wire\'s sharp end finds your palm. It always finds your palm.',
    ],
    cut: [
      'Snip. The bar that was waiting to impale somebody is now a stub.',
      'You cut the bar. It pings off into the fence. Nobody saw. Probably.',
      'The bar gives up with a clang. Your shoulder gives up quietly.',
      'Snip. The stub is safe now. The offcut is in your boot. You\'ll find it later.',
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
      '"{n} marks. Do you know how many marks a professional leaves? None. Do you know how many you left? I just told you."',
      '"The client\'s kid counted {n} footprints and asked if a giant lives there. I said no, just an idiot."',
      '"{n}! I\'m looking at the price of a grinder right now. Guess whose wages it comes out of."',
      '"You\'re not a finisher, you\'re a stamp collector. {n} stamps. Frame them. Hang them in the job centre."',
    ],
    managerAfter: [
      'He hangs up. Your ear is still ringing at 95%.',
      'He hangs up mid-word. You suspect the word was not "well done".',
      'The call ends. A pigeon on the formwork looks at you with something like pity.',
      'The call ends. Your phone is warm. Your face is warmer.',
      'He hangs up and texts you a single emoji. It is a brick.',
      'Silence. Then your phone buzzes: "Also you\'re doing Saturday."',
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
      msgs: ['EAT POOP', 'F*** YOU', 'NICE SLAB', 'SEND COFFEE', 'LOL', 'HI', 'NO', '42', 'WHY', 'OOPS', 'BAD JOB', 'GO HOME', 'SOS'],
      seen: 'Lights over the site. A saucer, no joke. It hovers over your slab, hums like a fridge, and burns "{m}" into it in neat capitals. Then it leaves. It didn\'t even say hello.',
      away: 'While you were away something wrote "{m}" into the slab, in perfect capitals, with no footprints leading to it. The dog is not talking.',
      voice: ['Greetings, concrete person. Your slab has been improved.', 'We have travelled forty light years to leave this message. You are welcome.', 'Take us to your foreman. Actually, don\'t.', 'Your species builds with liquid rock and then walks on it. Fascinating. Stupid, but fascinating.', 'We have probed many species. We will not be probing you. You smell of diesel.', 'Resistance is futile. So, apparently, is troweling.'],
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
      thin: ['"You poured it {d} mm thin. The client paid for {t}. Somebody\'s paying the difference, and I\'ll give you a clue: it\'s not the client."', '"Minimalist slab, is it? Minimal slab, minimal pay. I\'m adjusting yours now."', '"The client asked if it\'s a slab or a rumour. {d} mm thin. It\'s a rumour."', '"{d} millimetres short. You know what else is going to be short? Friday."', '"You saved concrete. Congratulations. Nobody asked you to save concrete. We have concrete. We don\'t have patience."'],
      thick: ['"{d} mm over? That\'s not a slab, that\'s a monument. The extra concrete comes out of your wages. Every cubic centimetre."', '"You know concrete costs money? Of course you don\'t. You\'ll know on Friday."', '"{d} mm over. Were you aiming for the laser or the ceiling?"', '"That\'s not a floor, that\'s a bunker. Expecting something?"', '"The engineer says the building now weighs more than the drawings. So does my disappointment."'],
      bumpy: ['"The laser says the floor has waves. Surfers love it. The client isn\'t a surfer."', '"±{r} mm. I\'ve seen flatter car parks after an earthquake."', '"The client put a marble on it. The marble is still rolling. It has a family now."', '"±{r} mm. I\'ve seen flatter mattresses. In skips."', '"It\'s not uneven, it\'s organic. That\'s what I\'ll tell the client. You can tell the job centre."'],
      slow: ['"The pour took {h}. The pump charges by the hour. Guess who\'s paying for the extra hour. Hint: it\'s the one reading this."', '"I could have poured that with a teaspoon faster. The teaspoon would also be cheaper."', '"{h} to pour that? My nan pours gravy faster, and she\'s been dead for six years."', '"The pump driver billed us for {h}. And for emotional support."', '"The truck waited so long the driver started a family. Congratulations to them."'],
      close: ['He hangs up before you can explain. There was going to be an explanation.', 'He hangs up. Even the dial tone sounds disappointed.', 'He\'s gone. You say "yes, boss" to nobody, out of habit.'],
    },
    pay: {
      base: 'Day rate',
      thin: ['Slab {d} mm thin — the client paid for {t}', 'Minimalist thickness surcharge'],
      thick: ['{d} mm over — free concrete, for the client', 'Monument surcharge ({d} mm proud)'],
      bumpy: ['Waves in the floor (±{r} mm)', 'Surf park fee (±{r} mm)'],
      slow: ['Pump overtime ({m} min of it)', 'The pour took {h}; the pump bills by the hour'],
      marks: ['{n} marks set in for ever, €4 each like a museum', 'Footprint archive ({n} exhibits)'],
      waste: ['{w} m³ dumped behind the office, €90 a cube', 'Surplus concrete ({w} m³), now a sculpture behind the office', 'Concrete you ordered, paid for and threw away: {w} m³'],
      wait: ['Waiting time paid to a man doing a crossword in a mixer', 'The mixer driver\'s crossword time, billed', 'Truck waiting time (the driver finished a novel)'],
      late: ['Late by {m} min. Time is money. Yours.', 'Late by {m} min. The birds noticed', 'Tardiness tax ({m} min)'],
      wrong: ['The wrong concrete, poured anyway', 'Wrong load, poured with confidence'],
      manager: ['Emotional damages (the manager\'s)', 'Therapy for the manager', 'The manager\'s blood pressure tablets'],
      falls: ['Dry cleaning: {n} × €10', 'Laundry, {n} × €10. The dog would have come home cleaner'],
      edges: ['Edges closed too late, {n} of them', 'Edges closed after they set ({n}). Brave, not clever'],
      edgesLate: ['{n} m of edge closed after it had set', 'Edging a stone ({n} m). Brave, not clever'],
      ufo: ['Unexplained lettering in the slab', 'Alien vandalism (not covered by insurance)', 'Removing an interstellar insult'],
      hell: ['Told {n} people to go to hell: no charge, company policy', 'Told {n} people to go to hell: free, and honestly the best part of your day'],
      shine: ['Bonus: it actually shines', 'Bonus: the client could see their face in it. They didn\'t like the face, but still'],
      noPan: ['No pan pass. The client paid for a floor, not a beach'],
      noBlade: ['No blade pass. You\'ll call it "matte finish". They won\'t', 'Shine not included'],
      roughEdges: ['Left rough: {n}. Like your manners', 'Rough edges ({n}): the client found them with a sock'],
      thrown: ['{n} tools thrown in the van: dents at cost, plus feelings', 'Tool abuse ({n} airborne)'],
      leftTools: ['Tools left on site ({n}): the yard sends a van for them, and you pay for the van', 'Abandoned equipment ({n}). The scrap man says thank you'],
      dirtyTools: ['Concrete on the tools ({n}), chiselled off by an apprentice who hates you', 'Tool cleaning ({n}), billed at therapy rates'],
      verdictGood: ['"Not bad. Don\'t let it go to your head, your head is already big enough."', '"Good slab. I\'ll pretend I did it when I tell the client."', '"Good work. Don\'t tell anyone I said that. Especially you."', '"If you keep this up I\'ll have to pay you properly. So don\'t keep it up."', '"The client cried. Good tears. I checked."'],
      verdictBad: ['"That\'s your whole day\'s pay gone. You know what that is? Character building."', '"You owe us money. We\'ll take it in coffee. Six months of coffee."', '"Next time I\'m hiring the dog. It leaves fewer marks and it works for sausages."', '"I\'ve seen better floors in a skip. The skip was cheaper, too."', '"Take tomorrow off. Take the month off. Take a hint."', '"Somewhere there\'s a job you\'d be good at. We haven\'t found it, but it\'s out there."', '"You owe us money. We\'ll take it out of your next life."'],
      verdictMeh: ['"It\'ll do. Things that \'will do\' are why I drink."', '"Could be worse. Could also be a lot better. It\'s mostly the second one."', '"It\'s fine. Fine is the worst word I know, and I just used it on you."', '"Not good, not bad. You\'re the beige of concrete."', '"The client says it\'s a floor. That\'s the nicest thing anyone\'s said about your work."'],
    },
    // said out loud now and then, when nothing else is being said
    thoughts: [
      '"My back makes a new noise now. It sounds like gravel."',
      '"My dad did concrete. His knees are in a museum."',
      '"The careers adviser said: something with your hands. She didn\'t say it would be this."',
      '"If I die here, pour me into the slab. At least I\'d finally be level."',
      '"Thirty more years of this and I\'ll have a lovely pension. Of about eleven euros."',
      '"Somewhere an office worker is complaining that the coffee machine is slow. Lovely for him."',
      '"There\'s concrete in my ear. I don\'t remember putting it there."',
      '"My doctor told me to avoid heavy lifting. I laughed so hard I pulled something."',
      '"Retirement plan: become the slab."',
      '"I should have been a dentist. Same kneeling. More money. Fewer dogs."',
      '"Every slab I pour will outlive me. Nice to be remembered as a car park."',
      '"Hands: cracked. Knees: gone. Spirit: set at ninety-five percent."',
      '"I\'m not tired. I\'m pre-cured."',
      '"One day robots will do this. The robots will also get yelled at. That\'s something."',
      '"Holiday this year: the other side of the van."',
      '"I could quit. I could. I won\'t. But I could."',
      '"Nobody ever wrote a song about a nice flat floor. Cowards."',
      '"If I lie down right here, how long before anyone notices? Probably the pump driver. For the wrong reasons."',
    ],
    // [from, voice, text]
    texts: [
      ['Mum', 'mum', '"Are you still doing concrete? Your cousin is a dentist now. Just saying."'],
      ['Mum', 'mum', '"Wear a hat. And call your mother. I am your mother."'],
      ['Mum', 'mum', '"Saw a documentary about backs. Yours was in it. The last ten minutes."'],
      ['Mum', 'mum', '"Your father wants to know if you\'re rich yet. I told him to sit down."'],
      ['Wife', 'partner', '"Dinner at 7. By which I mean I ate at 7."'],
      ['Wife', 'partner', '"The kids asked what you look like. I showed them a bag of cement."'],
      ['Wife', 'partner', '"If you\'re late again your side of the bed goes to the dog. The dog has already accepted."'],
      ['Wife', 'partner', '"Bring milk. And a reason to stay married. Milk first."'],
      ['The bank', 'bank', '"Your balance is low. Recommended action: pour faster."'],
      ['The bank', 'bank', '"Congratulations! Your overdraft has been promoted to a lifestyle."'],
      ['HR', 'hr', '"Reminder: mandatory wellbeing webinar at 14:00. Attendance is compulsory. Wellbeing is optional."'],
      ['HR', 'hr', '"Your holiday request has been received, laughed at, and filed."'],
      ['HR', 'hr', '"Please rate your happiness at work from one to ten. Answers below eight will be investigated."'],
      ['The physio', 'physio', '"Appointment reminder: your spine, Tuesday. Please bring as much of it as you can."'],
      ['Unknown number', 'spam', '"You have won a free cruise! Reply STOP to keep working."'],
      ['The client', 'client', '"Quick question: can the floor be heated? It\'s being poured right now? Great, so yes?"'],
      ['The client', 'client', '"My brother-in-law says you should use more concrete. He sells concrete."'],
      ['Your son', 'son', '"Teacher asked what you do. I said you make grey floors and swear at them. I got a sticker."'],
      ['Your daughter', 'daughter', '"Can I have the car when you die? It\'s for a school project."'],
      ['Your son', 'son', '"Dad, is concrete alive? It moves when you\'re not looking. I think it\'s alive."'],
      ['Your son', 'son', '"I told everyone at school you make roads. Now I have to make a road. Help."'],
      ['Your son', 'son', '"Grandma says your back is older than her. Can I have it when you\'re done with it?"'],
      ['Your son', 'son', '"I put your good boots in the bath to see if they float. They don\'t. Sorry. Well, not sorry, science."'],
      ['Your daughter', 'daughter', '"Daddy, I drew you at work. You\'re the grey one. Everything is grey. I ran out of the other colours."'],
      ['Your daughter', 'daughter', '"Mum says you\'ll be home by six. Then she laughed. Why did she laugh?"'],
      ['Your daughter', 'daughter', '"Can you make my dolls a floor? A flat one. Not like the kitchen."'],
      ['Your daughter', 'daughter', '"Teacher asked what I want to be when I grow up. I said not tired. She wrote it down."'],
      ['The dentist', 'dentist', '"You missed your appointment again. Your teeth have started seeing other people."'],
      ['The gym', 'gym', '"We miss you! It\'s been 814 days." You lift concrete for a living. The gym can shut up.'],
    ],
    radio: [
      '"Traffic news: a concrete mixer is stuck on the ring road. The driver says he\'s five minutes away."',
      '"And the weather: rain later, arriving at precisely the moment you don\'t want it."',
      '"Economy news: concrete up twelve percent. Concrete workers\' wages: still waiting for the truck."',
      '"Scientists confirm the human back was not designed for this. More at eleven."',
      '"And now a song for all the finishers out there. It\'s called Nobody Walk On It."',
      '"A caller asks if it\'s normal for a new floor to have footprints. Our expert says: only if you\'re lucky."',
      '"A study finds nine in ten builders talk to their tools more than their families. The tenth has no family. He has tools."',
      '"Breaking news: a local man has walked across fresh concrete. Police describe him as fine. For now."',
      '"You\'re listening to Site FM, the only station that plays the same four songs until you love them. Or break."',
      '"Competition time! The first caller who can name a day they weren\'t tired wins nothing. There have been no callers."',
      '"Horoscope for concrete workers: today the stars are aligned. Your spine is not."',
    ],
    neighbour: [
      '"Some of us are trying to sleep!" The window slams. Then opens again. "And your van\'s on my verge!"',
      '"Is that going to be a car park? I\'ll park there, then." It\'s a kitchen.',
      '"My nephew does concrete. He says you\'re doing it wrong." The nephew has never seen you.',
      '"In my day we poured floors by hand! With spoons!" The window closes on its own lie.',
      '"Can you keep the scraping down? I\'m on a video call!" You are also on a call. With your knees.',
      '"Was it you who parked the mixer on my tulips?" It was not. You\'ll take the blame anyway. It\'s that kind of day.',
      '"Lovely work! Very grey!" The only compliment you\'ll get this year. You write it down.',
      '"When you\'re done, can you do my patio? For free? Since you\'re here?"',
    ],
    homeEarly: 'The foreman: "95% or you sleep here." There\'s a sleeping bag in the van for a reason.',
    tooLate: {
      text: 'The slab is as hard as it will ever be, and it isn\'t finished: {what}. Blades now would only polish a stone.\n\nYour phone buzzes. The foreman: "It\'s set, isn\'t it. Throw the kit in the van and go home. We\'ll talk about it tomorrow. We\'ll talk about it a lot."',
      stare: ['You stare at it. It stares back. It doesn\'t get any softer.', 'You poke it with your boot. Your boot loses.', 'You wait for a miracle. The miracle is also on its lunch break.', 'You tell it you\'re disappointed. It doesn\'t care. It\'s concrete.', 'You kneel and whisper "why". The slab has no answer. The slab never has an answer.', 'You take a photo for the foreman. Then delete it. Then take another one. Worse.'],
    },
    packUp: {
      start: ['Every finisher\'s dream. You start throwing.', 'You march to the van, and the tools learn to fly.', 'Therapy is expensive. Throwing tools is free.', 'You don\'t pack the tools. You deliver them. At speed.', 'Health and safety would call this manual handling. You call it closure.'],
      hand: ['The hammer goes first. It had it coming.', 'The float spins like a helicopter. Nobody claps.', 'The pliers hit the van, then the van floor. Two dents for the price of one.', 'The hand trowel sails in like it knows the way.', 'The rebar cutter tumbles end over end and lands jaws-first, biting the van. Revenge.', 'The pliers go in. The wire goes in. A small piece of your dignity goes in with them.', 'The float flies like a javelin. Olympic form. Tragic context.'],
      machine: ['You throw a power trowel. You didn\'t know you could. Neither did your back.', 'The power trowel lands in the van with a noise the neighbours will describe to the police.', 'The power trowel lands on the float. The float is flatter now. Finally, something flat.', 'The edge trowel clears the door by a millimetre. The most accurate thing you\'ve done all day.'],
      rideOn: 'The ride-on stays where it is. It weighs 400 kg and you are angry, not strong.',
      laser: 'The laser goes in last-but-one. It beeps once in protest.',
      end: 'Door slammed. Engine on. You don\'t look back. The slab does.',
    },
  };

  // More of everything, so the same line comes round less often — and the player's own voice: what
  // comes out of your mouth when the slab wins another round.
  Object.assign(L, {
    // down in the concrete, said out loud
    meFall: [
      '"Shit. Again. Every single time."',
      '"Ow. My everything."',
      '"Nobody saw that. Nobody saw that. The pump driver saw that."',
      '"That\'s it, I\'m becoming an accountant."',
      '"My back just resigned. Effective immediately."',
      '"Cool. Cool cool cool. Concrete in my pants. Cool."',
      '"Stupid slab. Stupid boots. Stupid me."',
      '"Mum said get an office job. Mum was right."',
      '"I\'m fine! I\'m fine. I\'m not fine."',
      '"If I lie very still, maybe it will cure around me and I can stop."',
      '"Twenty years in the trade and I still fall like a newborn giraffe."',
      '"Ground: one. Me: nil."',
      '"My knees will remember this. They remember everything."',
      '"Well, that\'s the bum print sorted."',
      '"Help. No, don\'t help. Don\'t film it either."',
      '"There goes my dignity. It was the last one."',
      '"Gravity, you absolute bastard."',
      '"And that is why they pay me the big money. Ha. Ha."',
      '"I felt something go. Hopefully just the button."',
      '"This is fine. I wanted a sit down anyway."',
      '"Concrete in my ear. How is there concrete in my ear?"',
      '"Great. Now I\'m level with the slab. At last."',
      '"I think I landed on my phone. RIP phone."',
      '"Every fall takes a year off my life. I\'m technically dead since March."',
      '"That one was for the health and safety poster."',
      '"Nobody tell the foreman. He\'ll dock me for the dent."',
      '"Right. Getting up. In a minute. Or a week."',
      '"I\'ve seen tortoises with better balance."',
      '"The slab wanted a hug. The slab got one."',
      '"Next life I\'m a cat. Cats land on their feet and don\'t pour floors."',
    ],
    // one boot held by the concrete
    meStuck: [
      '"Shit, I\'m stuck again."',
      '"Stupid concrete got my leg again."',
      '"Let go. Let GO. It\'s not a hug, it\'s a boot."',
      '"Come on, you grey bastard. Give me my foot back."',
      '"It\'s got me. It\'s actually got me. Tell my family I trowelled well."',
      '"Why does it only ever want the left boot?"',
      '"If I pull my foot out of the boot, I\'m walking home in a sock. Again."',
      '"Quicksand in the films. Concrete in real life. Worse, because it\'s Tuesday."',
      '"One day they\'ll find me here like the man in the ice. Holding a float."',
      '"Stuck. Stuck like a fly in grey jam."',
      '"Every pour. Every single pour."',
      '"This boot cost ninety euros and it\'s staying here forever."',
      '"Wiggle. Wiggle. Wiggle. Nothing. Great."',
      '"The slab says I live here now."',
      '"My foot\'s asleep and the concrete\'s awake. Bad combination."',
      '"Concrete: nil. No — concrete: one. I\'m stuck."',
      '"Okay. Deep breath. Pull. Oh. Oh no. Deeper."',
      '"If the dog sees this I\'ll never hear the end of it."',
      '"You want the boot? Take the boot. Keep the sock as a tip."',
      '"This is how the pyramids got their workers. Stuck ones."',
      '"Slurp. That noise. I hate that noise."',
      '"I\'m not stuck, I\'m being held for questioning."',
      '"And there goes the other boot. Brilliant."',
      '"Future archaeologists: I was trying to get my boot back."',
    ],
    // a toe under the mesh before there's any concrete on it
    meTrip: [
      '"Who put that mesh there? Oh. Me."',
      '"Ow! Rebar, you spiteful little thing."',
      '"Every. Single. Chair. Every one."',
      '"That bar has had it in for me since this morning."',
      '"Mesh: one. My shin: bruised. Again."',
      '"I swear the rebar moves when I\'m not looking."',
      '"Stupid mesh caught my boot again."',
      '"Nearly lost a tooth to a bar chair. Nearly."',
      '"Walking on mesh is like walking on a trampoline made of revenge."',
      '"My shins look like a map of this site."',
      '"Who designs these things? Somebody who never walks on them."',
      '"Hop. Skip. Rebar. Ow."',
      '"The rebar and I are no longer on speaking terms."',
      '"Tied it myself, tripped on it myself. Teamwork."',
      '"Ah! My ankle. My good ankle. My only good ankle."',
      '"Fine. I\'ll walk like a flamingo."',
      '"The mesh bites. Nobody warns you that the mesh bites."',
      '"If I fall on that, they\'ll need the rebar cutter. For me."',
      '"Why is it always the one bar I didn\'t look at?"',
      '"Look at the steel, not the sky. Look at the steel."',
    ],
    // after the others have fallen you over
    meOw: [
      '"Ow."',
      '"Right. Okay. Ow."',
      '"I\'ll feel that tomorrow. And the day after. And in 2040."',
      '"That\'s going to bruise in the shape of a machine."',
      '"Worst day. Every day."',
      '"Nobody clap. Please."',
      '"And that is why I drink."',
      '"My chiropractor will buy a boat with this."',
      '"Nothing broken. Except my will to live."',
      '"Sure. Why not. Throw me about. Everybody else does."',
    ],
    // the morning after the night before
    hangoverCard: [
      'Last night: your cousin\'s birthday. You\'re still a bit drunk.',
      'Last night: "one beer" with the lads. There were eleven.',
      'Last night: karaoke. You sang the whole of Bohemian Rhapsody. Both parts.',
      'Last night: the neighbour\'s homemade vodka. The label said "for cleaning".',
      'Last night: a wedding. Not yours. You gave a speech anyway.',
      'Last night: you won the pub quiz. You don\'t remember the questions. Or the pub.',
      'Last night: sauna, then beer, then sauna, then beer. The Finnish way.',
      'Last night: a stag do. You woke up with one eyebrow.',
    ],
    meHangover: [
      '"Why is the sun so loud?"',
      '"Never again. I said that last time. I\'ll say it next time."',
      '"The ground is moving. Or I am. One of us is drunk."',
      '"Coffee. Coffee coffee coffee. And a new liver."',
      '"If the foreman comes close, I\'m breathing through my ears."',
      '"My head is a mixer drum. And it\'s turning."',
      '"Who drank all that? Oh. Me. Hero."',
      '"I can taste last night. It tastes like regret and peanuts."',
      '"Walk straight. Walk straight. Walk... that\'s not straight."',
      '"The slab is spinning. Slabs shouldn\'t spin."',
      '"I\'m not drunk, I\'m pre-cured."',
      '"Please nobody start the pump. Please nobody start anything."',
      '"My blood type today is lager."',
      '"If I close one eye the laser makes sense."',
      '"I\'ve got the shakes. Good for vibrating the concrete, at least."',
      '"Who put gravel in my mouth? Oh, that\'s my tongue."',
    ],
    meVomit: [
      '"Oh no. Oh no no no—"',
      '"Hold it... hold it... can\'t hold it."',
      '"Not on the slab. Not on the slab. Not on the— oh."',
      '"That\'s the kebab. And yesterday\'s kebab."',
      '"Better out than in. Worse on the boots."',
      '"I\'d like to apologise to the concrete."',
      '"Okay. Okay. I feel great now. I feel terrible."',
      '"There goes breakfast. And dignity. And a bit of dinner."',
      '"Did anyone see? The dog saw. The dog is judging."',
      '"Never again. Never. Ever. Until Friday."',
      '"That\'s a new colour. The client didn\'t order that colour."',
      '"Right. Back to work. Mouth tastes like a skip."',
    ],
    vomitNear: [
      'The pump driver watches you throw up, nods, and says: "Tuesday?"',
      'A dog walks over, sniffs it, and looks at you with genuine disappointment.',
      'Somebody across the road applauds. Slowly.',
      'A seagull lands and considers the buffet.',
    ],
    // the title screen, a different one every time
    titleJokes: [
      'Nobody likes playing with concrete. That\'s why they pay you. Barely.',
      'Nobody plays with concrete for fun. Except you, apparently. On your break.',
      'Concrete: the only thing on site harder than the foreman\'s heart.',
      'A concrete finisher\'s pension plan is a strong back and a short life.',
      'They say you can do anything with concrete. Except get your weekend back.',
      'Kids play in sandboxes. Adults get paid to play in a grey one. Badly.',
      'Every slab is flat until somebody puts a level on it.',
      'Concrete never forgets. Neither does the client.',
      'You don\'t choose concrete. Concrete chooses you, at 5 a.m., in the rain.',
      'The only thing that sets faster than concrete is your will to live.',
      'Your dentist has a better back than you. Your dentist has a better everything than you.',
      'The pump is on time, the truck is late, the dog is here. A normal day.',
    ],
    // a little extra misery, handed out at random
    mishaps: [
      { text: 'A seagull swoops down and takes your sandwich. The whole thing. Didn\'t even say thanks.', me: '"Oi! That was my lunch! You flying rat!"', effect: 'energy' },
      { text: 'Your phone slips out of your pocket and lands face down. On the concrete. Of course.', me: '"No. No no no. Not the phone. Not again."', effect: 'phone' },
      { text: 'A wasp decides you are the most interesting thing on site. You disagree, loudly.', me: '"Get away! Get AWAY! I\'m allergic to— I don\'t know, I\'m allergic to wasps now!"', effect: 'run' },
      { text: 'Your trousers split. All the way. The breeze is educational.', me: '"Oh, lovely. Air conditioning."', effect: 'trousers' },
      { text: 'A bird does its business on your helmet. From a great height, with great precision.', me: '"That\'s good luck, right? That\'s what people say to make you feel better."', effect: '' },
      { text: 'Your knee pad falls off and slides down the site like it\'s trying to escape. Relatable.', me: '"Even the knee pad wants to go home."', effect: '' },
      { text: 'You sneeze eleven times in a row. The concrete dust has opinions.', me: '"Achoo! Eleven. New record."', effect: '' },
      { text: 'You find a sandwich in your pocket. From Thursday. You eat it anyway.', me: '"Tastes like Thursday."', effect: 'energy+' },
      { text: 'Your lighter dies. So does your last hope of a smoke break.', me: '"Of course. Of course it does."', effect: '' },
      { text: 'Somebody\'s car alarm goes off and keeps going. It sounds like your thoughts.', me: '"Make it stop. Make everything stop."', effect: '' },
      { text: 'A kid on a scooter shouts "Why are you playing with mud?" and rides off before you can explain your life.', me: '"It\'s not mud, it\'s concrete! It\'s... never mind."', effect: '' },
      { text: 'The wind takes your delivery note, your hat, and very nearly your will to live.', me: '"My hat! Fine. Keep it. Everybody take something."', effect: '' },
      { text: 'Your boot lace snaps. You tie it with rebar wire. Fashion.', me: '"Tie-wire. Is there anything it can\'t do."', effect: '' },
      { text: 'You bite your tongue while concentrating. The taste of blood and cement: your signature dish.', me: '"Ow. Tongue. Why."', effect: '' },
      { text: 'A pigeon walks past with more confidence than you\'ve had in years.', me: '"Look at him. No mortgage. No knees."', effect: '' },
    ],
  });
  L.thoughts.push(
    '"I could\'ve been a lawyer. I\'d still be lying on the floor, but indoors."',
    '"The float and I have been together longer than most marriages."',
    '"Concrete is just rocks that got peer-pressured into a shape."',
    '"When I die, don\'t bury me. Pour me. Nice finish, pans and blades."',
    '"My body is sixty percent water and forty percent regret."',
    '"Some people meditate. I stare at a slab until it hardens. Same thing."',
    '"The doctor asked if I lift heavy things. I laughed so hard I pulled something."',
    '"One day I\'ll be a supervisor and I\'ll ruin somebody else\'s knees."',
    '"If my back was a slab, it\'d be rejected."',
    '"Pension plan: win the lottery. Backup plan: fall off something expensive."',
    '"Nobody grows up wanting to do this. You just wake up one day with a float in your hand."',
    '"My kids think I build houses. I build the floor. The bit everybody walks on. Like me."',
    '"The trowel hums. I hum. We\'re both machines at this point."',
    '"I wonder if the concrete dreams. Probably of better finishers."',
    '"Holiday this year: a long weekend lying on a flat surface I didn\'t make."',
    '"My hands smell of cement even after a shower. Even after two."',
    '"Rain later. Of course. The sky has my schedule."',
    '"I\'ve spent more mornings with the pump driver than with my family."',
    '"What\'s the difference between me and the slab? The slab gets to rest."',
    '"If I had a euro for every footprint, I could pay for the footprints."',
    '"Somewhere an office worker is complaining about his chair. Lucky man."',
    '"When they make a movie of my life it\'ll be three hours of waiting and ten seconds of panic."',
    '"The laser beeps like my heart monitor will one day."',
    '"I was going to be a footballer. Now I kneel for a living anyway."',
    '"Coffee is the only colleague who never lets me down."',
    '"They call it curing. Nothing about this is a cure."',
    '"The slab is flatter than my bank account. Just."',
    '"My knees sound like a bag of crisps."',
  );
  L.texts.push(
    ['Mum', 'mum', '"Your aunt says concrete workers die young. I said not you, you\'re too stubborn."'],
    ['Mum', 'mum', '"Did you eat? A sandwich is not a meal. Concrete dust is not a seasoning."'],
    ['Wife', 'partner', '"The washing machine made a noise after your trousers. It\'s dead. Your trousers killed it."'],
    ['Wife', 'partner', '"Remember we have dinner with my parents. Try not to smell like a skip."'],
    ['Wife', 'partner', '"The kids asked what you do. I said you make the ground. They think you\'re God. Don\'t."'],
    ['The bank', 'bank', '"Your overdraft misses you. Please visit soon. It will be bigger."'],
    ['The bank', 'bank', '"Congratulations! You have been pre-approved for more debt."'],
    ['HR', 'hr', '"Reminder: mandatory wellbeing session on Friday. Attendance is compulsory. Wellbeing is optional."'],
    ['HR', 'hr', '"Please stop listing your back as a colleague on the timesheet."'],
    ['The client', 'client', '"Quick question: can it be heated? And polished? And ready by lunch? Thanks!"'],
    ['The client', 'client', '"My brother-in-law says concrete dries in an hour. He sells phones."'],
    ['Physio', 'physio', '"Your appointment is confirmed. Please bring your spine, or what is left of it."'],
    ['Gym', 'gym', '"We miss you! Your membership still works! Unlike your knees!"'],
    ['Unknown number', 'spam', '"Congratulations, you have won a free holiday! Just send your bank details and both kidneys."'],
    ['Dentist', 'dentist', '"Please stop grinding your teeth in your sleep. Your teeth are not a float."'],
    ['The foreman', 'foreman', '"Where are you? Don\'t answer. I can see the van. I can see you. Move."'],
    ['The foreman', 'foreman', '"Client asked if we can do it cheaper. I said yes. You\'ll figure it out."'],
  );
  L.radio.push(
    '"And now the weather for construction workers: yes."',
    '"A study finds concrete workers are the happiest people in Europe. The study was written by a concrete company."',
    '"Health news: sitting is the new smoking. Kneeling on concrete is the new everything."',
    '"Local man wins lottery, immediately quits concrete. Slab left half-finished. Nobody blames him."',
    '"Coming up: ten songs about trucks, and a man who thinks he can sing."',
    '"Traffic: a mixer has overturned on the motorway. Drivers are advised to go round, or pick up a float."',
    '"The price of houses is up again. The price of the people who build them is not."',
    '"Tonight: a documentary about bridges. Spoiler: somebody poured the bottom bit and nobody thanked them."',
    '"And a request for Mart on a site somewhere: his wife says the dog can have his side of the bed."',
    '"Breaking: scientists grow concrete that heals itself. Finishers: when do we get that?"',
    '"The pension age is going up again. So is the average age of finishers who make it."',
    '"Our phone-in today: tell us your worst day at work. Line one, you\'re on site. Oh, he\'s crying."',
  );
  L.neighbour.push(
    '"Do you have to breathe so loud?" You haven\'t said anything. It\'s the pump.',
    '"My cat is traumatised!" The cat is asleep on your van.',
    '"I\'m calling the council!" The council is on a coffee break too.',
    '"Is it true you can put a body in concrete?" The window closes very slowly.',
    '"When I was young, men built things with their hands!" He is holding a TV remote.',
    '"Keep it down! Some of us work nights!" He is wearing a dressing gown at noon.',
    '"Nice floor! Shame about the colour!" It\'s grey. It\'s always grey.',
    '"Can you do my patio after? Cash. Half cash. Mostly biscuits."',
    '"My grandson does this! He says it\'s easy!" Grandson is twelve.',
    '"Every morning! Every morning with the beeping!" The laser beeps once. She screams.',
  );
  L.driverTalk.push(
    'Pump driver: "The trick to this job is don\'t look at the price of diesel. Or your life."',
    'Pump driver: "My doctor says I need more exercise. I watch you. It counts."',
    'Pump driver: "I\'ve pumped for forty years. The boom is the only thing on me that still goes up."',
    'Pump driver: "My ex-wife is a concrete finisher now. She says it\'s less abusive."',
    'Pump driver: "Faster than yesterday\'s bloke. He cried. You\'re not crying. Yet."',
    'Pump driver: "You know why the line blocks? Because it can smell fear."',
    'Pump driver: "I\'d help, but union rules. Also my back. Also I don\'t want to."',
    'Pump driver: "Last guy I worked with fell in. We called it a feature wall."',
    'Pump driver: "Ever wonder what\'s at the bottom of every slab? Other finishers."',
    'Pump driver: "If you die, can I have your float? It\'s a good float."',
  );
  L.mixTalk.push(
    'Mixer driver: "I\'ve got three more after you. Pour like you mean it."',
    'Mixer driver: "The plant added something new. Nobody knows what. Enjoy."',
    'Mixer driver: "My drum\'s been turning since 1998. So have I, mentally."',
    'Mixer driver: "Slump test? I dropped a cone once. It was sad."',
    'Mixer driver: "Last site had a guy fall in the chute. We charged him delivery."',
    'Mixer driver: "The trick is to never look at the invoice. Or the finisher."',
    'Mixer driver: "You want water in it? Legally, no. Emotionally, yes."',
    'Mixer driver: "I\'ve seen a man trowel with a snow shovel. Don\'t be that man. Close, though."',
  );
  L.pourJokes.push(
    'A crow lands on the formwork and watches you like the foreman does. Same eyes.',
    'The client arrives in white trainers. You and the concrete look at each other.',
    'The pump driver\'s phone plays a ringtone of a crying baby. He lets it ring. It\'s his.',
    'Somebody shouts "Nice one!" from a passing car. You don\'t know what they mean. Neither do they.',
    'The mixer drum burps. It sounds exactly like your stomach.',
    'An old man stops to watch. He watches for forty minutes. He says: "Wet, isn\'t it." He leaves.',
    'The foreman texts a thumbs up. You don\'t know what for. You\'ll be blamed for it later.',
    'The concrete slaps against your boots with the sound of a thousand disappointed parents.',
  );
  L.falls.push(
    'You go down like a sack of cement, which is appropriate.',
    'Feet out, arms up, a moment of flight, then the full grey embrace.',
    'You fall with the grace of a fridge down stairs.',
    'You land on your side and make a snow angel. A concrete angel. A sad angel.',
    'You slip. For a second you are weightless and free. Then you are neither.',
    'You fall. The laser beeps. Even the laser finds it funny.',
  );
  L.stuck.push(
    'The concrete grabs your boot and won\'t give it back. Like the bank with your money.',
    'Your boot sinks and stays. The slurp is the saddest noise on site.',
  );
  L.foreman.push(
    '"The client wants it shinier. I said you\'d lick it. Don\'t make me a liar."',
    '"I\'m not saying you\'re slow. I\'m saying the concrete is waiting for you."',
    '"Remember: if you get hurt, do it after the pour. Paperwork is a nightmare."',
    '"Good news, you\'re doing the next one too. Bad news: it\'s tomorrow. At four."',
    '"I told the client you\'re the best we\'ve got. Don\'t make me explain what that says about us."',
    '"Health and safety are coming Thursday. Look safe. Don\'t be safe, just look it."',
  );
  L.coffee.push(
    'Coffee. Black, like your future.',
    'You drink it too hot. Your tongue files a complaint.',
    'The coffee is cold. So is the tea. So is the love in your life.',
    'Coffee number three. Your hands now vibrate at the same speed as the power trowel.',
    'You find a dead fly in the cup. Protein.',
    'Coffee. Somewhere a doctor shakes his head.',
  );
  L.shoutBack.push(
    '"No need to shout, I\'m not deaf!" They are a bit deaf. They go round.',
    '"Unbelievable. The attitude." They go round, loudly.',
    '"I pay your wages!" They don\'t. They go round anyway.',
    '"This is a public pavement!" It isn\'t. They go round, muttering about rights.',
  );
  L.crossAnyway.push(
    'They cross with the confidence of a man who has never paid for a floor.',
    '"It\'s basically dry!" It is basically not.',
    'They tiptoe. Tiptoeing makes deeper holes. Science.',
    'They cross while filming it for their followers. Both of them.',
  );
  L.flip.person.push(
    ['flip2', 'They flip you off with both hands. They\'ve had practice.'],
    ['flip1', '"Real mature!" They are also giving you the finger. Everybody is mature today.'],
    ['clutch', 'They blow you a kiss. That\'s worse. That\'s so much worse.'],
  );
  L.flip.nothing.push(
    'You flip off the clock. It keeps going. Rude.',
  );
  // Whoever is in the toilet, answering the door handle. Everybody has these; some have their own.
  L.looInside = [
    '"Occupied! And I\'d give it a while. And a match. And a priest."',
    '"Knock again and I\'m taking the last of the paper with me."',
    '"I\'m not coming out. I live here now. Forward my post."',
    '"If I don\'t make it, tell my wife I died doing what I loved. Sitting down."',
    '"There\'s no paper. There was never any paper. Have you got a receipt? A long one?"',
    '"Go away, I\'m writing my will. On the wall. With a marker."',
    '"Give me five minutes. Or fifty. The kebab decides, not me."',
    '"Busy! I\'m making the air unbreathable for the next man. That\'s you."',
    '"This box has seen things. Now it\'s seeing me. Poor box."',
    '"Do you mind? It\'s the only quiet five minutes I\'ll get this year."',
    '"I\'ve been in here so long the concrete\'s gone off. Both lots."',
  ];
  L.looInsideBy = {
    pump: ['"The pump bills by the hour whether I\'m on it or on this. Relax."', '"I\'m running the pump from in here. The remote works through walls. Mostly."', '"Not now. I\'m in the middle of a very long stroke."'],
    truck: ['"The drum keeps turning. So does my stomach. Wait your turn."', '"Nine cubes on the truck, one in here. Give me a minute."'],
    kebab: ['"You want to know why the special is special? Wait till I come out."', '"Yesterday\'s kebab, returning to sender."'],
    office: ['"I\'m in a meeting!" You can hear him scrolling his phone.', '"Site engineer! I\'m inspecting the facilities. Thoroughly."'],
    stranger: ['A voice you have never heard: "Who\'s that? Do I work here? Does anyone?"', '"I don\'t even work here. I saw a toilet and I believed."'],
  };
  L.looFume = ['"Fine! FINE! I\'m hurrying!" He is not hurrying.', '"You rattle that handle once more and I\'m staying till Christmas."'];
  L.looOut = [
    '"All yours. I\'d give it ten minutes. And a gas mask."',
    '"Don\'t light a match. I mean it."',
    '"The paper\'s gone. You\'ll have to improvise. I believe in you."',
    '"Sorry. Sorry about that. Truly. Sorry."',
    '"I left the seat up. And some other things."',
  ];
  L.helper.talk.push(
    '"I don\'t usually do floors. I don\'t usually do work."',
    '"My last job was painting. I like it here less."',
    '"Is it normal that I can\'t feel my legs?"',
    '"The manager said you were a legend. He meant a warning."',
    '"Do we get lunch? Do we get to sit? Do we get to live?"',
    '"I once saw a man drown in a slab. Only up to his knees, but still."',
  );

  // The manager: an opener, a rant and a parting shot, picked separately — so he is never quite the
  // same twice, just always angry.
  Object.assign(L, {
    mgrOpen: [
      '"It\'s me. Don\'t talk."',
      '"Are you sitting down? Don\'t. Stand up. Work."',
      '"I\'m going to say this once, and then again, louder."',
      '"Hello? HELLO? Oh, you\'re there. Pity."',
      '"I\'m in the car, so I\'ll be brief. I won\'t be brief."',
      '"My blood pressure called. It wants to talk to you."',
      '"Quick question, and I want a wrong answer so I can shout."',
      '"I\'ve just had a very interesting phone call. About you. Interesting like a car crash."',
      '"Before you say anything: no."',
      '"I\'m calling from the dentist. That\'s how much I need to shout at you."',
      '"Take the phone away from your ear. Further. That\'s it."',
      '"Guess who. Guess why."',
      '"Listen. No. LISTEN."',
      '"I\'m on holiday. On a beach. And I\'m calling YOU. Think about that."',
      '"My wife says I shout too much. She hasn\'t met you."',
    ],
    mgrRant: [
      '"Your timesheet says eight hours. The van\'s GPS says six of them were at the kebab stand."',
      '"Somebody put diesel receipts through as lunch. The lunch was four hundred litres."',
      '"There\'s a new scratch on the van. Shaped like your face. Explain."',
      '"The client wants to know why the floor has a slope. I told him it\'s for drainage. There is no drain."',
      '"You used eleven rolls of tie-wire last week. Are you knitting? Are you knitting on my time?"',
      '"HR says you called a pump driver a \'hydraulic idiot\'. He\'s my cousin. He IS a hydraulic idiot. Don\'t say it."',
      '"Accounts want to know what \'moral support coffee\' is and why it\'s forty euros."',
      '"Somebody posted a video of you falling in the slab. It has more views than our website."',
      '"The quote said one day. It\'s been one day. Why does it still look like soup?"',
      '"I got a letter from the neighbour. Handwritten. Seven pages. You\'re in all of them."',
      '"The last finisher I had quit to become a monk. The monks sent him back."',
      '"You know what\'s cheaper than you? Nothing. And yet somehow you\'re still expensive."',
      '"I\'ve been in this business thirty years. Thirty. And I have never seen anybody trowel like a toddler finger-painting."',
      '"The insurance company called. They asked if you were a real person or a test."',
      '"The client saw you sitting down. SITTING. In daylight."',
      '"Your float is on my expense report as \'misc. weapon\'. Why?"',
      '"The planning office asked if the slab was meant to be abstract art."',
      '"I told the client you were our best man. He laughed. I laughed. Then I stopped laughing."',
      '"The laser rental place called. They want the laser back. And an apology. For what you said to it."',
      '"Somebody left the van running for three hours. It\'s now officially a heater I pay for."',
      '"I found a sandwich in the van from 2019. I know it\'s yours. It has your attitude."',
      '"Every time you call in sick, the company share price goes up. We\'re not on the stock market. It goes up anyway."',
      '"The safety officer asked for your training certificate. You gave him a kebab menu."',
      '"We had a complaint that you were singing. Badly. On a Tuesday."',
      '"The pump company doubled our price. They said: \'because of him.\' They didn\'t say your name. They didn\'t need to."',
      '"If the client asks, we did a vapour barrier. We did a vapour barrier, right? RIGHT?"',
      '"The foreman says you\'re doing fine. That means you\'re doing badly and he\'s scared of you."',
      '"The office printer has run out of ink printing your mistakes."',
      '"You asked for a raise. I\'ve decided to raise my expectations instead."',
      '"There is concrete on my car. I wasn\'t on site. How is there concrete on my car?"',
    ],
    mgrClose: [
      '"Fix it. Fix it now. Fix it yesterday."',
      '"I\'m going to hang up now, and you\'re going to feel bad."',
      '"We\'ll talk about this at your review. Your final review."',
      '"Don\'t call me back. Especially if it\'s important."',
      '"And smile. The client might be watching. He isn\'t. Smile anyway."',
      '"Love you. That was a slip. I hate you. Get back to work."',
      '"This call has been recorded for my own amusement."',
      '"I\'m taking it out of your wages. And your holidays. And your soul."',
      '"If you need me, I\'m at the golf course. Don\'t need me."',
      '"My doctor says I shouldn\'t shout. My doctor has never met you. Goodbye."',
      '"Next time I call, I want good news. Or silence. Silence is fine."',
      '"Right. Well. That\'s my day ruined. Yours next."',
    ],
    // concrete over the formwork
    mgrSpill: [
      '"{v} cubic metres ON THE GROUND? That\'s not a slab, that\'s a flood! The gravel doesn\'t need a floor!"',
      '"The pump driver says you\'re pouring over the boards like it\'s a waterfall feature. It\'s a garage."',
      '"I pay for concrete in the formwork, not in the flowerbed! {v} cubes! Do you know what a cube costs? More than you!"',
      '"The neighbour sent me a photo of concrete going over the edge. He added music. Sad music."',
      '"There\'s a formwork. It has a top. The concrete goes UNDER the top. It\'s not complicated. It\'s very uncomplicated."',
      '"You\'re not pouring a slab, you\'re pouring my profit into a hole in the gravel."',
      '"Did you think the formwork was a suggestion? It\'s wood. Wood doesn\'t suggest. Wood holds."',
      '"{v} cubic metres of waste. I could build a small bunker out of your mistakes. I might. I might live in it."',
      '"The client asked if we\'re doing the driveway too. We\'re not. You did anyway. With spillage."',
      '"Keep the hose in the middle! The MIDDLE! The edges are for trowels and regret!"',
    ],
    meSpill: [
      '"Oh no. Over the board. Over the board!"',
      '"That\'s going in the gravel. That\'s money going in the gravel."',
      '"Stop! Stop! Too much at the edge!"',
      '"Oops. The formwork runneth over."',
      '"Nobody saw that. The manager will see that."',
      '"Ah, crap. There goes somebody\'s wages. Mine."',
      '"Too high, too high! Move the hose!"',
    ],
    // the shovel, digging where it shouldn't
    meDig: [
      '"Oops. That\'s a hole now."',
      '"Too much. Way too much. That\'s a pond."',
      '"Now I\'ve dug a pit. Brilliant. Somebody fill it. Me. I\'ll fill it."',
      '"The laser is going to scream at that."',
      '"That\'s not levelling, that\'s archaeology."',
    ],
  });
  L.managerCall.push(
    '"{n} marks. {n}! I could play hopscotch on that slab. I might. I might do it in your boots."',
    '"I\'ve counted the marks from a drone. {n}. The drone is also disappointed."',
    '"{n} footprints. The client asked if we hired a herd of goats. I said no. I should have said yes. Goats are cheaper."',
    '"If you leave {n} marks in my slab again, I\'ll leave one on your payslip. A big one. With a minus."',
    '"The client\'s kid pointed at the slab and said: \'Look, a map!\' {n} marks. Of your failures."',
    '"Do you know what a polished floor looks like? It doesn\'t look like {n} people danced on it."',
    '"The architect cried. Real tears. {n} times. Once for every mark."',
  );
  L.managerAfter.push(
    'He\'s still talking when you put the phone in your pocket. Your pocket gets told off too.',
    'The call ends. Your ear rings for another ten minutes. It\'s also angry.',
    'He hangs up. Then calls back just to say "AND ANOTHER THING" and hangs up again.',
    'You check your wages app. It\'s already smaller.',
  );
  L.badPour.thin.push(
    '"{d} millimetres thin. You poured it like a budget airline pours coffee."',
    '"The drawing says {t}. You did {t} minus your talent."',
  );
  L.badPour.thick.push(
    '"{d} mm too thick. You\'ve poured the slab and a bit of next year\'s slab too."',
    '"Did you think {t} was a minimum? It\'s not a minimum. It\'s a number."',
  );
  L.badPour.bumpy.push(
    '"Plus or minus {r}. My golf course is flatter. My golf course is a hill."',
    '"The client put a marble on it. The marble got seasick."',
  );
  L.badPour.slow.push(
    '"{h} to pour it. Snails have poured faster. Snails with a union."',
    '"The truck driver went on his pension while you were pouring."',
  );
  L.badPour.close.push(
    '"And don\'t think I won\'t tell your mother."',
    '"I\'m writing all of this down. In red. I bought a new red pen for you."',
  );

  // ------------------------------------------------------------------ one day
  let day;
  function newDay() {
    ENTRY.j = irnd(3, NZ - 4);
    const shape = makeShape();
    const season = pick(['winter', 'spring', 'summer', 'summer', 'autumn', 'autumn']);
    const baseTemp = { winter: rnd(-3, 4), spring: rnd(6, 14), summer: rnd(16, 27), autumn: rnd(3, 11) }[season];
    const indoor = chance(0.25);
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
      indoor,
      boom: !indoor && chance(shape.area > 80 ? 0.65 : 0.35),
      surpriseBlowout: chance(0.2),
      helper: shape.area >= 90 && chance(0.85),
      rebarLift: chance(0.5) ? rnd(0.2, 0.7) : 0,
      pumpHelps: chance(0.5),
      breakTool: chance(0.45) ? pick(shape.area > 50 ? ['trowelBig', 'trowelSmall', 'rideOn'] : ['trowelBig', 'trowelSmall']) : null,
      breakAfter: rnd(15, 60),
      trouble: weighted([[0.55, null], [0.3, 'wrong'], [0.15, 'empty']]),
      troubleTruck: chance(0.6) ? 1 : 2,
      wrongKind: pick(['grade', 'screed', 'fibre', 'other']),
    };
  }
  /** Where people and dogs come from today: anywhere, or on an inside day only through the open ends. */
  function sidesToday() { return day && day.indoor ? ['w', 'e'] : SIDES; }
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
  /** The pace through the set: slow to start, quick through the middle, slow at the end. A load that's in and level on a big pour skips the slow start. */
  function stageMul(H, fast) { return H < 15 ? (fast ? 1.2 : 0.6) : H < 85 ? 1.2 : 0.7; }

  // Every truck is a mix of its own, batched at its own time: it starts going off when it lands,
  // at its own pace — a load with retarder in it lags, a hot one runs ahead. So the slab doesn't
  // harden as one: the end the first truck filled can take the pans while the last truck's end
  // is still soup, and each square is as hard as the load it's made of.
  const MIXES = {
    standard: { rate: 1.0, say: 'Standard mix.' },
    retarded: { rate: 0.74, say: 'Retarder in this one: it\'ll set slower than the last.' },
    accelerated: { rate: 1.32, say: 'Accelerator in it. This one goes off fast. Get it flat quick.' },
    hot: { rate: 1.15, say: 'It\'s warm from the plant. It\'ll go off quicker.' },
    wet: { rate: 0.9, say: 'Wetter than the last. Slower, and it bleeds.' },
  };
  /** The load a square is made of, if it's made of one yet. */
  function mixOf(c) { const l = c && c.load ? gs.loads[c.load] : null; return l && l.at !== null ? l : null; }
  /** How hard one square is: the load it's made of, or the slab's figure if nothing's landed there. */
  function cellH(c) { const l = mixOf(c); return l ? l.h : gs.H; }
  /** Hardness at a point: the squares round it, so the edge between two loads isn't a cliff. */
  function hAt(x, z) {
    let n = 0, sum = 0;
    for (const [dx, dz] of [[-0.35, -0.35], [0.35, -0.35], [-0.35, 0.35], [0.35, 0.35]]) {
      const c = cellAt(x + dx, z + dz);
      if (c) { sum += cellH(c); n++; }
    }
    return n ? sum / n : gs.H;
  }
  /** The slab as one figure, for the clock and the HUD: the average over the concrete. */
  function meanH() {
    let n = 0, sum = 0;
    for (const c of gs.cells) if (c.fill > 20) { sum += cellH(c); n++; }
    return n ? sum / n : gs.H;
  }
  /** Whether there's concrete at a point to work on: poured there already, or the pour is over. */
  function concreteIn(x, z) {
    if (gs.phase !== 'pour' && gs.phase !== 'wash' && gs.phase !== 'cure') return false;
    const c = cellAt(x, z);
    return !!c && (gs.poured || c.fill > 20);
  }
  /** The softest and hardest of it. */
  function spreadH() {
    let lo = 100, hi = 0;
    for (const c of gs.cells) if (c.fill > 20) { const h = cellH(c); lo = Math.min(lo, h); hi = Math.max(hi, h); }
    return hi >= lo ? [lo, hi] : [gs.H, gs.H];
  }
  /** A load's worth landing in a square: whichever load has put most in, the square is. */
  function addLoad(c, mm) {
    const n = gs.truckNo;
    if (!n || !gs.loads[n]) return;
    const l = gs.loads[n];
    if (l.at === null) l.at = gs.t;
    // fresh concrete over work already done on it: the pass and the edge have to be done again
    if ((c.covP || c.covB || c.edged) && mm > 0.5) { c.covP = c.covB = false; c.edged = 0; c.ep = null; surfDirty = true; }
    if (!c.load) { c.load = n; c.loadMm = mm; return; }
    if (c.load === n) { c.loadMm += mm; return; }
    // fresh on top of a load that has started to go off: the two won't marry
    const old = gs.loads[c.load];
    if (old && old.h > 12 && c.fill > 30) coldJoint(old, l);
    c.otherMm += mm;
    if (c.otherMm > c.loadMm) { c.load = n; [c.loadMm, c.otherMm] = [c.otherMm, c.loadMm]; }
  }
  let coldSaid = 0;
  function coldJoint(old, now) {
    gs.coldJoint += 1;
    if (performance.now() < coldSaid) return;
    coldSaid = performance.now() + 30000;
    toast(`Truck ${now.no} going onto truck ${old.no}'s concrete, which has started to set (${Math.floor(old.h)}%). A cold joint: they won't bond properly.`, 'warn');
    if (!gs.coldJointNoted) { gs.coldJointNoted = true; remember(`Truck ${now.no} went onto truck ${old.no}'s concrete after it had started to set. There's a cold joint in the slab now.`); }
  }
  /** Minutes until [target]%: the slab's figure, or one load's from where it is at its own pace. */
  function etaTo(target, from, rate, fast) {
    let H = from === undefined ? gs.H : from, t = gs.t, m = 0;
    if (H >= target) return 0;
    while (H < target && m < 4000) { H += cureRate(t) * stageMul(H, fast) * (rate || 1) * 5; t += 5; m += 5; }
    return m;
  }
  /*
   * A pour of three trucks or more goes on for hours, and the concrete that's in and levelled
   * doesn't sit there waiting for the last truck: it's been in the drum an hour, it's been
   * worked, the bleed water is off it, and it gets on with going off. A load that has finished
   * coming and lies level (within about 8 mm of the laser height) skips the slow start, so the
   * end the first trucks filled is ready for the trowels while the last ones are still pouring.
   */
  function bigPour() { return trucksOrdered() + gs.extraTrucks > 2; }
  function loadIsLevel(l) {
    let n = 0, dev = 0;
    for (const c of gs.cells) if (c.load === l.no && c.fill > 20) { n++; dev += Math.abs(c.fill - day.thick); }
    return n >= 3 && dev / n <= 8;
  }
  function loadsGoingOff() {
    if (gs.phase !== 'pour' || !bigPour()) return;
    for (const l of gs.loads) {
      if (!l || l.fast || l.at === null) continue;
      // all of it in: an earlier truck, or this one gone and the next not here yet
      const allIn = l.no < gs.truckNo || (l.no === gs.truckNo && !gs.truck);
      if (!allIn || !loadIsLevel(l)) continue;
      l.fast = true;
      toast(`Truck ${l.no}'s concrete is in and level, and it's getting on with going off. That end will be ready for the trowels long before the last truck.`, 'good');
    }
  }
  /** The hardest load in the slab so far: the end the first trucks filled. */
  function firstLoad() {
    let best = null;
    for (const l of gs.loads) if (l && l.at !== null && (!best || l.h > best.h)) best = l;
    return best;
  }

  // ------------------------------------------------------------------ state
  let gs;
  function freshState() {
    // the whole grid, for looking a square up by where it is; `cells` is just the slab's
    const grid = [];
    for (let j = 0; j < NZ; j++) for (let i = 0; i < NX; i++) {
      grid.push({ i, j, idx: j * NX + i, on: !!(day && day.on[j * NX + i]), fill: 0, pan: 0, blade: 0, covP: false, covB: false, marks: [], defect: false, load: 0, loadMm: 0, otherMm: 0, edged: 0 });
    }
    const cells = grid.filter((c) => c.on);
    return {
      loads: [], coldJoint: 0,
      t: 4 * 60 + 45, phase: 'title',
      arrived: 0,
      energy: 76, cups: 3, sausage: false,
      tool: 'hands', carrying: null, toolsUnloaded: false,
      tools: {}, fit: { trowelSmall: 'pans', trowelBig: 'pans', rideOn: 'pans' }, fitting: null,
      prep: { unload: false, form: [false, false], laser: false }, laserInVan: true,
      dirt: {}, broken: {}, runSecs: {}, charges: [], leftBehind: [], dirtyAtEnd: [], yelledTools: false,
      needs: { wee: rnd(10, 30), poo: rnd(0, 20), warned: {} },
      laserOn: false, laserBattery: true, laserPacked: false,
      pumpAt: 0, pumpHere: false, pipes: 0, pipesGone: 0,
      trucks: [], truck: null, truckNo: 0, truckWaitPaid: 0, pourMins: 0, gaveUp: false, packing: null, thrown: null,
      mixFactor: 1, water: 0, mixState: 'ok',
      blocked: -1, blowout: null, blowoutDone: false, batteryDone: false, fellInPour: false, stuckUntil: 0,
      pourStarted: false, pourDone: false, pourEnd: 0, pouredM3: 0, waste: 0, extraTrucks: 0,
      washed: false,
      H: 0, poured: false,
      panPasses: [], bladePasses: [],
      edgesDone: 0, edgeNotes: [], edgeLate: 0,
      waitMode: null, fastForward: null,
      schedule: [], nextNuisance: Infinity, rained: false, lastSleep: 0,
      milestones: {},
      stats: { hell: 0, crossed: 0, dogs: 0, falls: 0, prints: 0, repaired: 0, blockages: 0, coffee: 0, own: 0, dug: 0, flips: 0 },
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
  canvas.addEventListener('webglcontextlost', (e) => { e.preventDefault(); document.body.classList.add('glLost'); }, false);
  canvas.addEventListener('webglcontextrestored', () => { document.body.classList.remove('glLost'); resize(); }, false);

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

  /** A small texture painted by [draw] on a [w] x [h] canvas, repeated [rx] x [ry]. */
  function paintTex(w, h, rx, ry, draw) {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    draw(c.getContext('2d'), w, h);
    const t = new THREE.CanvasTexture(c);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(rx, ry);
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
  box(1.14, 0.7, 1.14, new THREE.MeshLambertMaterial({ color: 0x8fb3c4, transparent: true, opacity: 0.5 }), 0, 0.56, 0, ibc);   // the water in it
  box(1.3, 0.15, 1.3, 0x6c7176, 0, 0.12, 0, ibc);
  cyl(0.05, 0.05, 0.25, 0x333333, 0, 0.35, 0.7, ibc).rotation.x = Math.PI / 2;
  // the galvanised cage round it, the filler cap, the tap's handle
  const cageTex = paintTex(32, 32, 4, 4, (g, w) => { g.strokeStyle = '#fff'; g.lineWidth = 3; g.strokeRect(1.5, 1.5, w - 3, w - 3); });
  box(1.26, 1.14, 1.26, new THREE.MeshLambertMaterial({ color: 0xa9aeb3, map: cageTex, alphaMap: cageTex, alphaTest: 0.5, side: THREE.DoubleSide }), 0, 0.77, 0, ibc);
  cyl(0.12, 0.12, 0.06, 0x2a2d31, 0, 1.33, 0, ibc, 12);
  box(0.16, 0.03, 0.03, 0x2c6ad6, 0, 0.43, 0.8, ibc);
  ibc.position.set(POS.ibc.x, 0, POS.ibc.z);
  scene.add(ibc);

  // kebab stand
  const kiosk = new THREE.Group();
  box(3.2, 2.6, 2.6, 0x2f6f6a, 0, 1.3, 0, kiosk);
  const awnTex = paintTex(64, 16, 5, 1, (g, w, h) => { for (let x = 0; x < w; x += 16) { g.fillStyle = '#d83a2e'; g.fillRect(x, 0, 8, h); g.fillStyle = '#f4efe6'; g.fillRect(x + 8, 0, 8, h); } });
  box(3.6, 0.12, 1.3, new THREE.MeshLambertMaterial({ map: awnTex }), 0, 2.5, 1.8, kiosk);
  for (let k = 0; k < 9; k++) { const sc = cyl(0.2, 0.2, 0.12, k % 2 ? 0xf4efe6 : 0xd83a2e, -1.6 + k * 0.4, 2.42, 2.45, kiosk, 10, ); sc.rotation.x = Math.PI / 2; sc.scale.set(1, 1, 0.5); }
  box(3.2, 0.9, 0.05, 0x1b2224, 0, 1.6, 1.31, kiosk);
  // behind the hatch: the spit, glowing, and the heater behind it
  box(0.4, 0.6, 0.02, new THREE.MeshBasicMaterial({ color: 0xff7a2a }), 0.8, 1.6, 1.34, kiosk);
  cyl(0.13, 0.09, 0.55, 0x8a4a22, 0.8, 1.6, 1.47, kiosk, 10);
  box(3.3, 0.07, 0.4, 0xb9bec3, 0, 1.14, 1.48, kiosk);                                // the counter
  sign(['KEBAB  9 €', 'SAUSAGE  4 €', 'COFFEE  2 €', 'CAN  2 €'], 0.9, 0.75, '#1d1f22', '#ffd23f', kiosk, -1.05, 1.63, 1.34, { align: 'left', weight: 700 });
  [-0.6, 0.4].forEach((x) => { cyl(0.03, 0.04, 0.7, 0x5a5f64, x, 0.35, 2.05, kiosk, 8); cyl(0.17, 0.17, 0.06, 0xd83a2e, x, 0.72, 2.05, kiosk, 14); });
  cyl(0.22, 0.2, 0.75, 0x2e5d3a, 1.95, 0.38, 1.6, kiosk, 12);                         // the bin
  cyl(0.15, 0.15, 0.6, 0xd8392f, -1.75, 0.3, -0.9, kiosk, 12);                        // the gas bottle
  cyl(0.05, 0.05, 0.12, 0x5a5f64, -1.75, 0.66, -0.9, kiosk, 8);
  const kLabel = textSprite('KEBAB & COFFEE', { w: 3.2, color: '#ffd23f' });
  kLabel.position.set(0, 3.3, 0);
  kiosk.add(kLabel);
  kiosk.position.set(POS.kiosk.x, 0, POS.kiosk.z);
  kiosk.rotation.y = 0.5;
  scene.add(kiosk);

  // site office container and the portable toilet, for the atmosphere
  const office = new THREE.Group();
  const ribTex = paintTex(64, 8, 12, 1, (g, w, h) => { const gr = g.createLinearGradient(0, 0, w, 0); [0, 0.25, 0.5, 0.75, 1].forEach((p, k) => gr.addColorStop(p, k % 2 ? '#c9d6e2' : '#ffffff')); g.fillStyle = gr; g.fillRect(0, 0, w, h); });
  box(6, 2.6, 2.4, new THREE.MeshLambertMaterial({ color: 0x3c6e9e, map: ribTex }), 0, 1.3, 0, office);
  box(1.36, 0.96, 0.03, 0xd8dcdf, 1.2, 1.6, 1.205, office);                            // the window's frame
  box(1.2, 0.8, 0.05, 0x1e2a33, 1.2, 1.6, 1.21, office);
  box(0.02, 0.8, 0.055, 0xd8dcdf, 1.2, 1.6, 1.21, office);
  box(0.95, 2.05, 0.05, 0x2a4f73, -1.6, 1.08, 1.215, office);                         // the door
  box(0.35, 0.3, 0.052, 0x1e2a33, -1.6, 1.65, 1.217, office);
  box(0.12, 0.03, 0.05, 0xb9bec3, -1.25, 1.05, 1.24, office);
  box(1.1, 0.18, 0.7, 0x6c7176, -1.6, 0.09, 1.6, office);                             // the steel step
  box(0.62, 0.46, 0.32, 0xe9e7e2, 2.3, 2.1, 1.35, office);                            // the air conditioner
  cyl(0.17, 0.17, 0.02, 0x5a5f64, 2.3, 2.1, 1.52, office, 16).rotation.x = Math.PI / 2;
  sign(['SITE OFFICE', 'Hard hats beyond this point'], 1.5, 0.45, '#f4f4f0', '#1d1f22', office, 0.1, 2.25, 1.215, { border: '#2c6ad6' });
  [[-3, -1.2], [3, -1.2], [-3, 1.2], [3, 1.2]].forEach(([x, z]) => [0.07, 2.53].forEach((y) => box(0.16, 0.14, 0.16, 0x2a2d31, x, y, z, office)));
  office.position.set(POS.office.x, 0, POS.office.z);
  office.rotation.y = 0.1;
  scene.add(office);
  const loo = new THREE.Group();
  box(1.1, 2.2, 1.1, 0x2c6ad6, 0, 1.1, 0, loo);
  box(1.2, 0.08, 1.2, 0x1d4a99, 0, 2.24, 0, loo);
  box(0.8, 1.9, 0.04, 0x1d4a99, 0, 1.0, 0.56, loo);
  // the knob you pull, and the little window over it: red when somebody's in, green when not
  cyl(0.035, 0.035, 0.05, 0x9ea3a8, 0.28, 1.05, 0.6, loo, 10).rotation.x = Math.PI / 2;
  const looSignMat = new THREE.MeshBasicMaterial({ color: 0x2fbf5a });
  box(0.13, 0.05, 0.02, looSignMat, 0.28, 1.17, 0.585, loo);
  box(0.16, 0.08, 0.012, 0x1d4a99, 0.28, 1.17, 0.578, loo);
  cyl(0.05, 0.05, 0.45, 0x1d4a99, -0.35, 2.45, -0.35, loo, 8);                       // the vent pipe
  sign(['WC'], 0.24, 0.16, '#f4f4f0', '#1d4a99', loo, 0, 1.75, 0.585);
  [-1, 1].forEach((sd) => { for (let k = 0; k < 4; k++) box(0.012, 0.03, 0.3, 0x1d4a99, sd * 0.556, 2.0 - k * 0.05, 0, loo); });
  loo.position.set(POS.loo.x, 0, POS.loo.z);
  scene.add(loo);

  // The clutter of a site: a skip, a pallet of blocks, a heap of sand, timber, cones, a barrow, a
  // cable drum, a generator, a bundle of bars. Somewhere new every day, and in your way. Each is
  // [group, radius, half x, half z, colour on the map].
  const props = [];
  function prop(r, hx, hz, col, build) { const g = new THREE.Group(); build(g); g.traverse((o) => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; } }); scene.add(g); props.push([g, r, hx, hz, col]); return g; }
  prop(2.2, 1.8, 1.0, '#e0a21a', (g) => {
    box(3.4, 0.08, 1.7, 0xd99a12, 0, 0.1, 0, g);
    [-1, 1].forEach((sd) => { box(3.4, 1.1, 0.07, 0xe0a21a, 0, 0.65, sd * 0.85, g); const e = box(0.07, 1.1, 1.75, 0xe0a21a, sd * 1.9, 0.62, 0, g); e.rotation.z = sd * 0.35; });
    [[-0.8, 0.4], [0.3, -0.2], [0.9, 0.3], [-0.2, 0.1]].forEach(([x, z]) => { box(rnd(0.3, 0.7), rnd(0.2, 0.5), rnd(0.3, 0.6), pick([0x8f918e, 0x6a4a32, 0xb9b5aa]), x, 0.35, z, g).rotation.y = rnd(0, 3); });
    sign(['SKIP HIRE'], 1.4, 0.3, '#1d1f22', '#f2b705', g, 0, 0.8, 0.89);
  });
  const blockTex = paintTex(64, 64, 2, 2, (gc) => {
    gc.fillStyle = '#c9c6bf'; gc.fillRect(0, 0, 64, 64);
    gc.fillStyle = '#9c9a94';
    for (let y = 0; y < 64; y += 16) { gc.fillRect(0, y, 64, 2); for (let x = (y / 16) % 2 ? 16 : 0; x < 64; x += 32) gc.fillRect(x, y, 2, 16); }
  });
  prop(1.2, 0.65, 0.55, '#bdb8ad', (g) => {
    box(1.2, 0.12, 1.0, 0xa47a4a, 0, 0.06, 0, g);
    box(1.14, 0.9, 0.94, new THREE.MeshLambertMaterial({ map: blockTex }), 0, 0.57, 0, g);
    box(1.16, 0.02, 0.96, new THREE.MeshLambertMaterial({ color: 0xdfe8ee, transparent: true, opacity: 0.35 }), 0, 1.03, 0, g);
  });
  prop(1.5, 1.3, 1.3, '#c9b27a', (g) => {
    const heap = mesh(new THREE.ConeGeometry(1.35, 0.85, 14), new THREE.MeshLambertMaterial({ map: noiseTex([201, 178, 122], 40, 3) }), 0, 0.42, 0, g);
    heap.scale.z = 0.8;
  });
  prop(1.8, 1.6, 0.5, '#a47a4a', (g) => {
    [-1.1, 1.1].forEach((x) => box(0.12, 0.1, 0.9, 0x6a4a32, x, 0.05, 0, g));
    for (let k = 0; k < 7; k++) box(3.1, 0.05, 0.19, pick([0xc49a6a, 0xb88c5a, 0xd0a878]), rnd(-0.08, 0.08), 0.13 + Math.floor(k / 4) * 0.06, -0.3 + (k % 4) * 0.2, g);
  });
  prop(1.0, 0.9, 0.3, '#ff6b1a', (g) => {
    [-0.6, 0, 0.6].forEach((x) => {
      box(0.34, 0.04, 0.34, 0x1d1f22, x, 0.02, 0, g);
      cyl(0.02, 0.14, 0.62, 0xff6b1a, x, 0.35, 0, g, 12);
      cyl(0.07, 0.095, 0.1, 0xf2f0ea, x, 0.4, 0, g, 12);
    });
  });
  prop(1.0, 0.8, 0.4, '#2e8b3a', (g) => {
    const tray = mesh(new THREE.CylinderGeometry(0.5, 0.32, 0.32, 4, 1, true), new THREE.MeshLambertMaterial({ color: 0x2e8b3a, side: THREE.DoubleSide }), 0, 0.55, 0, g);
    tray.rotation.y = Math.PI / 4; tray.scale.set(1.3, 1, 0.9);
    const wh = cyl(0.18, 0.18, 0.08, 0x1d1f22, 0.72, 0.18, 0, g, 14); wh.rotation.x = Math.PI / 2;
    [-1, 1].forEach((sd) => { box(1.3, 0.035, 0.035, 0x5a5f64, 0.05, 0.42, sd * 0.24, g).rotation.z = 0.12; box(0.035, 0.36, 0.035, 0x5a5f64, -0.35, 0.2, sd * 0.24, g); });
  });
  prop(0.8, 0.5, 0.5, '#6a4a32', (g) => {
    [-0.28, 0.28].forEach((z) => { const d = cyl(0.55, 0.55, 0.05, 0xa47a4a, 0, 0.55, z, g, 18); d.rotation.x = Math.PI / 2; });
    const core = cyl(0.36, 0.36, 0.52, 0x1d1f22, 0, 0.55, 0, g, 16); core.rotation.x = Math.PI / 2;
  });
  prop(0.9, 0.6, 0.4, '#f2b705', (g) => {
    box(1.0, 0.55, 0.6, 0xf2b705, 0, 0.42, 0, g);
    box(1.08, 0.04, 0.68, 0x2a2d31, 0, 0.72, 0, g);
    [-1, 1].forEach((sd) => box(1.08, 0.04, 0.04, 0x2a2d31, 0, 0.12, sd * 0.32, g));
    box(0.3, 0.2, 0.02, 0x2a2d31, -0.25, 0.45, 0.31, g);
    box(0.05, 0.05, 0.02, new THREE.MeshBasicMaterial({ color: 0x3ec16a }), -0.3, 0.5, 0.325, g);
    cyl(0.03, 0.03, 0.2, 0x5a5f64, 0.35, 0.82, 0.1, g, 8);
  });
  prop(1.6, 1.6, 0.35, '#8e4b2c', (g) => {
    [-1, 1].forEach((x) => box(0.1, 0.1, 0.7, 0x6a4a32, x, 0.05, 0, g));
    for (let k = 0; k < 10; k++) { const b = cyl(0.012, 0.012, 3, 0x8e4b2c, rnd(-0.05, 0.05), 0.12 + Math.floor(k / 5) * 0.03, -0.12 + (k % 5) * 0.06, g, 5); b.rotation.z = Math.PI / 2; }
  });

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
    // each post in its concrete foot, and a rail along the top
    const feet = new THREE.InstancedMesh(new THREE.BoxGeometry(0.6, 0.14, 0.24), lam(0x9a9c99), posts.length);
    const q2 = new THREE.Quaternion(), up2 = new THREE.Vector3(0, 1, 0), one = new THREE.Vector3(1, 1, 1), at2 = new THREE.Vector3();
    posts.forEach(([x, z], k) => { m4.compose(at2.set(x, 0.07, z), q2.setFromAxisAngle(up2, Math.abs(x) === 44 ? Math.PI / 2 : 0), one); feet.setMatrixAt(k, m4); });
    scene.add(feet);
    box(88, 0.04, 0.04, 0x9aa1a7, 0, 1.92, -34); box(88, 0.04, 0.04, 0x9aa1a7, 0, 1.92, 34);
    box(0.04, 0.04, 68, 0x9aa1a7, -44, 1.92, 0);
    box(0.04, 0.04, 28, 0x9aa1a7, 44, 1.92, -20); box(0.04, 0.04, 28, 0x9aa1a7, 44, 1.92, 20);
    const mesh2 = new THREE.MeshBasicMaterial({ color: 0x9aa1a7, transparent: true, opacity: 0.18, side: THREE.DoubleSide });
    box(88, 1.8, 0.02, mesh2, 0, 1.0, -34); box(88, 1.8, 0.02, mesh2, 0, 1.0, 34);
    box(0.02, 1.8, 68, mesh2, -44, 1.0, 0);
    box(0.02, 1.8, 28, mesh2, 44, 1.0, -20); box(0.02, 1.8, 28, mesh2, 44, 1.0, 20);
  })();

  // a work light on a pole by the van, for the dark end of the day
  const lampPole = cyl(0.07, 0.09, 8, 0x5a5f64, -21, 4, 3);
  const lamp = box(0.8, 0.35, 0.5, 0xfff3d6, -21, 8, 3);
  lamp.material = new THREE.MeshBasicMaterial({ color: 0x777777 });

  // the laser on its tripod — shown once it is set up
  const tripod = new THREE.Group();
  [0, 2.1, 4.2].forEach((a) => {
    const leg = cyl(0.02, 0.02, 1.5, 0xd8b23a, Math.cos(a) * 0.25, 0.7, Math.sin(a) * 0.25, tripod);
    leg.rotation.z = Math.cos(a) * 0.3;
    leg.rotation.x = -Math.sin(a) * 0.3;
    // the lower, telescoped half of each leg, and the steel point it's stamped in with
    const low = cyl(0.014, 0.014, 0.5, 0x9ea3a8, Math.cos(a) * 0.43, 0.22, Math.sin(a) * 0.43, tripod, 6);
    low.rotation.copy(leg.rotation);
    cyl(0.012, 0.001, 0.07, 0x5a5f64, Math.cos(a) * 0.5, 0.02, Math.sin(a) * 0.5, tripod, 5);
  });
  cyl(0.12, 0.12, 0.04, 0xd8b23a, 0, 1.4, 0, tripod, 14);
  const laserHead = new THREE.Group();
  box(0.22, 0.2, 0.22, 0xd84a2a, 0, 0, 0, laserHead);
  // the glass band the beam comes out of, the cap over it, a carry handle, the battery door
  cyl(0.085, 0.085, 0.07, 0x1d2a33, 0, 0.13, 0, laserHead, 16);
  cyl(0.09, 0.09, 0.025, 0x2a2d31, 0, 0.18, 0, laserHead, 16);
  box(0.16, 0.02, 0.03, 0x2a2d31, 0, 0.23, 0, laserHead);
  [-1, 1].forEach((sd) => box(0.02, 0.05, 0.03, 0x2a2d31, sd * 0.07, 0.205, 0, laserHead));
  box(0.12, 0.1, 0.01, 0x3a3d41, 0, -0.02, 0.113, laserHead);
  box(0.05, 0.02, 0.012, 0x3ec16a, 0.03, 0.05, 0.114, laserHead);
  const beam = new THREE.Mesh(new THREE.BoxGeometry(14, 0.01, 0.01), new THREE.MeshBasicMaterial({ color: 0xff3b30, transparent: true, opacity: 0.55 }));
  beam.position.x = 7;
  laserHead.add(beam);
  laserHead.position.y = 1.5;
  tripod.add(laserHead);
  tripod.position.set(POS.tripod.x, 0, POS.tripod.z);
  tripod.visible = false;
  scene.add(tripod);

  // ------------------------------------------------------------------ vehicles
  /**
   * A flat sign: [lines] of text painted on a [w] x [h] metre panel, in [fg] on [bg]. Number plates,
   * the name on the van, the menu at the kebab stand. It faces +z; turn it to face where it should.
   */
  function sign(lines, w, h, bg, fg, parent, x, y, z, opts) {
    opts = opts || {};
    const c = document.createElement('canvas'), px = 256;
    c.width = px; c.height = Math.max(16, Math.round((px * h) / w));
    const g = c.getContext('2d');
    if (bg) { g.fillStyle = bg; g.fillRect(0, 0, c.width, c.height); } else g.clearRect(0, 0, c.width, c.height);
    if (opts.border) { g.strokeStyle = opts.border; g.lineWidth = 4; g.strokeRect(2, 2, c.width - 4, c.height - 4); }
    g.fillStyle = fg; g.textAlign = opts.align || 'center'; g.textBaseline = 'middle';
    const lh = c.height / lines.length;
    lines.forEach((l, k) => {
      const size = Math.min(lh * 0.72, (px * 1.6) / Math.max(4, l.length));
      g.font = `${opts.weight || 800} ${size}px Manrope, Roboto, sans-serif`;
      g.fillText(l, opts.align === 'left' ? 10 : px / 2, lh * (k + 0.5));
    });
    const tex = new THREE.CanvasTexture(c);
    tex.encoding = THREE.sRGBEncoding;
    tex.anisotropy = 4;
    const m = mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshLambertMaterial({ map: tex, transparent: !bg, alphaTest: bg ? 0 : 0.3 }), x, y, z, parent);
    return m;
  }
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
  const vanWs = glassSlope(van, 2.92, 1.4, 2.0, 2.28, 1.8, 0.035);
  const vanSideGlass = box(0.95, 0.55, 2.08, GLASS, 1.45, 1.9, 0, van);
  const vanStripe = box(5.75, 0.16, 2.07, 0xff6b1a, 0.3, 1.12, 0, van);
  box(0.16, 0.3, 2.1, 0x2a2d31, 3.32, 0.62, 0, van);
  box(0.16, 0.3, 2.1, 0x2a2d31, -2.66, 0.62, 0, van);
  box(0.05, 0.28, 1.1, 0x2a2d31, 3.33, 0.92, 0, van);
  [-1, 1].forEach((s) => box(0.14, 0.2, 0.08, 0x2a2d31, 2.05, 1.72, s * 1.1, van));
  box(0.02, 1.6, 0.02, 0x9aa1a7, -2.63, 1.4, 0, van);       // the rear doors' seam
  box(3.6, 0.05, 1.5, 0x5a5f64, -0.4, 2.56, 0, van);
  [-0.45, 0.45].forEach((z) => box(3.4, 0.05, 0.06, 0xc0c4c8, -0.4, 2.63, z, van));
  for (let x = -1.9; x <= 1.1; x += 0.4) box(0.04, 0.04, 0.9, 0xc0c4c8, x, 2.64, 0, van);
  [[-1.7, 1.0], [-1.7, -1.0], [2.3, 1.0], [2.3, -1.0]].forEach(([x, z]) => wheel(van, x, z, 0.38, 0.26));
  // the grille and its badge, the plates, the tail lights, the doors' seams, the orange light on the
  // roof, and the firm's name down both sides
  box(0.04, 0.22, 0.95, 0x1a1b1d, 3.33, 0.84, 0, van);
  [0.78, 0.86, 0.94].forEach((y) => box(0.05, 0.018, 0.9, 0x44484d, 3.34, y, 0, van));
  cyl(0.05, 0.05, 0.02, 0xb9bec3, 3.36, 0.86, 0, van, 14).rotation.z = Math.PI / 2;
  sign(['FIN', 'ABC-123'], 0.52, 0.12, '#f4f4f0', '#111', van, 3.42, 0.6, 0, { border: '#111' }).rotation.y = Math.PI / 2;
  sign(['ABC-123'], 0.52, 0.12, '#f4f4f0', '#111', van, -2.76, 0.72, 0, { border: '#111' }).rotation.y = -Math.PI / 2;
  [-1, 1].forEach((sd) => {
    box(0.04, 0.34, 0.12, 0xb3261e, -2.66, 1.02, sd * 0.86, van);
    box(0.045, 0.08, 0.12, 0xff9a1a, -2.665, 1.24, sd * 0.86, van);
    [0.95, 2.35, -0.25].forEach((x) => box(0.012, x < 0 ? 1.55 : 1.35, 0.004, 0x5a5f64, x, x < 0 ? 1.25 : 1.2, sd * 1.028, van));
    box(0.14, 0.035, 0.012, 0x2a2d31, 2.2, 1.42, sd * 1.03, van);                  // the cab door's handle
    const nm = sign(inMixMaster ? ['CONWIC', 'FLOORS FOR LIVING'] : ['POUR DAY', 'CONCRETE FLOORS'], 2.4, 0.5, null, '#2a1c10', van, -0.7, 1.62, sd * 1.032);
    if (sd < 0) nm.rotation.y = Math.PI;
  });
  cyl(0.09, 0.1, 0.05, 0x1d1f22, 1.35, 2.5, 0, van, 10);
  const vanBeacon = cyl(0.07, 0.08, 0.1, new THREE.MeshBasicMaterial({ color: 0xff8a1a }), 1.35, 2.57, 0, van, 10);
  void vanBeacon;
  // the back: two doors that swing right round, a ramp that slides out and down, and just inside
  // the door the laser in its orange case and a bucket of this and that
  const vanBack = { doors: [], open: 0, want: 0 };
  [-1, 1].forEach((s) => {
    const hinge = new THREE.Group();
    hinge.position.set(-2.7, 0, s * 0.98);
    box(0.05, 1.8, 0.96, 0xe9e7e2, 0, 1.4, -s * 0.48, hinge);
    box(0.07, 0.12, 0.08, 0x2a2d31, -0.02, 1.35, -s * 0.86, hinge);
    box(0.06, 0.16, 0.97, 0xff6b1a, -0.005, 1.12, -s * 0.48, hinge);
    van.add(hinge);
    vanBack.doors.push({ hinge, s });
  });
  const vanInside = box(0.02, 1.75, 1.85, 0x15171a, -2.665, 1.38, 0, van);
  vanInside.visible = false;
  const RAMP_TOP = P(-2.76, 0.78), RAMP_RUN = 2.64;
  const RAMP_DOWN = Math.atan2(RAMP_TOP.z, RAMP_RUN);
  const RAMP_LEN = Math.hypot(RAMP_RUN, RAMP_TOP.z);
  const rampPivot = new THREE.Group();
  rampPivot.position.set(RAMP_TOP.x, RAMP_TOP.z, 0);
  box(RAMP_LEN, 0.05, 1.6, 0x8d9297, -RAMP_LEN / 2, 0, 0, rampPivot);
  for (let k = 1; k < 9; k++) box(0.04, 0.025, 1.55, 0x5a5f64, -k * RAMP_LEN / 9, 0.035, 0, rampPivot);
  rampPivot.visible = false;
  van.add(rampPivot);
  const laserCase = new THREE.Group();
  box(0.62, 0.34, 0.42, 0xd84a2a, 0, 0.17, 0, laserCase);
  box(0.2, 0.05, 0.05, 0x2a2d31, 0, 0.37, 0, laserCase);
  laserCase.position.set(-3.05, 0.72, 0.36);
  laserCase.rotation.z = RAMP_DOWN;
  van.add(laserCase);
  const vanBucket = cyl(0.17, 0.14, 0.34, 0x2a2d31, -3.0, 0.9, -0.42, van, 12);
  vanBucket.rotation.z = RAMP_DOWN;
  laserCase.visible = vanBucket.visible = false;
  const vanLabel = textSprite('THE VAN', { w: 2.2 });
  vanLabel.position.set(0, 3.2, 0);
  van.add(vanLabel);
  van.position.set(POS.van.x, 0, POS.van.z);
  van.rotation.y = -0.25;
  scene.add(van);

  // Inside the cab, for a nap behind the wheel: the dash, the wheel, the seats, your legs, what
  // lives on the dash of a work van, and glass you can see the site through. It's only there while
  // you're in it; the outside's own glass is dark, so that goes while you're in.
  const vanCab = new THREE.Group();
  vanCab.visible = false;
  van.add(vanCab);
  const vanClock = { canvas: document.createElement('canvas'), last: -1 };
  vanClock.canvas.width = 128; vanClock.canvas.height = 40;
  vanClock.tex = new THREE.CanvasTexture(vanClock.canvas);
  (function cabInside() {
    const g = vanCab;
    const see = new THREE.MeshLambertMaterial({ color: 0xbfd3de, transparent: true, opacity: 0.12, depthWrite: false });
    const DASH = 0x2b2d30, TRIM = 0x3d4046, SEAT = 0x34373c, LINER = 0xc2beb4, PANTS = 0x3a3226;
    glassSlope(g, 2.92, 1.4, 2.0, 2.28, 1.8, 0).material = see;
    [-1, 1].forEach((sd) => {
      box(1.02, 0.66, 0.02, see, 1.47, 1.86, sd * 0.955, g);                        // the side window
      const pil = box(Math.hypot(0.92, 0.88), 0.08, 0.07, DASH, 2.46, 1.84, sd * 0.92, g);
      pil.rotation.z = Math.atan2(0.88, -0.92);                                     // the pillar by the windscreen
      box(1.3, 0.92, 0.05, TRIM, 1.55, 0.98, sd * 0.95, g);                          // the door, from inside
      box(0.5, 0.06, 0.1, 0x25272b, 1.5, 1.24, sd * 0.89, g);                        // its armrest
      box(0.1, 0.04, 0.03, 0x9ea3a8, 1.85, 1.3, sd * 0.915, g);                      // its handle
      box(1.1, 0.06, 0.06, TRIM, 1.45, 2.22, sd * 0.94, g);                          // over the window
      box(0.06, 0.7, 0.06, TRIM, 0.95, 1.86, sd * 0.94, g);                          // the pillar behind you
    });
    box(0.14, 0.1, 1.9, TRIM, 1.97, 2.31, 0, g);                                    // over the windscreen
    box(1.25, 0.04, 1.9, LINER, 1.38, 2.39, 0, g);                                  // the roof lining
    box(1.9, 0.04, 1.9, 0x1d1f22, 1.65, 0.53, 0, g);                                // the floor mat
    box(0.05, 1.9, 1.9, 0x5a5f64, 0.76, 1.45, 0, g);                                // the bulkhead, the tools behind it
    // the dash: a long shelf under the windscreen, the clocks in front of you, the radio in the middle
    box(0.55, 0.42, 1.86, DASH, 2.47, 1.1, 0, g);
    box(0.72, 0.05, 1.86, DASH, 2.55, 1.35, 0, g).rotation.z = -0.1;
    box(0.2, 0.1, 0.44, DASH, 2.2, 1.42, -0.45, g);
    [-0.52, -0.38].forEach((z, k) => {
      const dial = cyl(0.052, 0.052, 0.01, 0x0d0f11, 2.094, 1.4, z, g, 18);
      dial.rotation.z = Math.PI / 2;
      mesh(new THREE.TorusGeometry(0.052, 0.004, 4, 20), new THREE.MeshBasicMaterial({ color: 0xff9a3c }), 2.088, 1.4, z, g).rotation.y = Math.PI / 2;
      box(0.004, 0.042, 0.006, new THREE.MeshBasicMaterial({ color: 0xffffff }), 2.086, 1.39, z, g).rotation.x = k ? -0.4 : 0.9;
    });
    box(0.04, 0.1, 0.26, 0x1a1b1d, 2.215, 1.2, 0.02, g);
    const radio = mesh(new THREE.PlaneGeometry(0.2, 0.06), new THREE.MeshBasicMaterial({ map: vanClock.tex }), 2.193, 1.2, 0.02, g);
    radio.rotation.y = -Math.PI / 2;
    // the wheel, tipped back towards you like a bus's, on its column into the dash
    const wheelG = new THREE.Group();
    wheelG.position.set(1.95, 1.42, -0.45);
    wheelG.rotation.z = -0.62;
    g.add(wheelG);
    mesh(new THREE.TorusGeometry(0.19, 0.022, 8, 30), 0x1a1b1d, 0, 0, 0, wheelG).rotation.y = Math.PI / 2;
    [0, 2.2, 4.1].forEach((a) => { box(0.02, 0.17, 0.035, 0x2a2c30, 0, Math.cos(a) * 0.085, Math.sin(a) * 0.085, wheelG).rotation.x = a; });
    cyl(0.055, 0.055, 0.04, 0x2a2c30, 0, 0, 0, wheelG, 14).rotation.z = Math.PI / 2;
    cyl(0.035, 0.045, 0.45, 0x25272b, 0.22, 0, 0, wheelG, 10).rotation.z = Math.PI / 2;
    [-0.58, -0.45, -0.3].forEach((z, k) => { box(0.1, 0.02, 0.07, 0x2a2c30, 2.05, 0.66 + k * 0.01, z, g).rotation.z = 0.6; });
    cyl(0.012, 0.014, 0.28, 0x2a2c30, 1.9, 1.02, -0.08, g, 8).rotation.z = 0.35;       // the gear stick
    mesh(new THREE.SphereGeometry(0.03, 10, 8), 0x1a1b1d, 1.86, 1.15, -0.08, g);
    // the seats; the passenger's has the hard hat on it
    [-1, 1].forEach((sd) => {
      box(0.52, 0.14, 0.5, SEAT, 1.28, 0.98, sd * 0.45, g);
      box(0.13, 0.72, 0.5, SEAT, 0.98, 1.4, sd * 0.45, g).rotation.z = 0.14;
      box(0.1, 0.18, 0.3, SEAT, 0.93, 1.88, sd * 0.45, g);
    });
    mesh(new THREE.SphereGeometry(0.13, 14, 8, 0, Math.PI * 2, 0, Math.PI / 2), 0xf2f0ea, 1.3, 1.05, 0.45, g);
    cyl(0.16, 0.16, 0.012, 0xf2f0ea, 1.33, 1.055, 0.45, g, 18);
    // your legs, asleep: thighs along the seat, shins down to the pedals, gloves in your lap
    [-0.55, -0.35].forEach((z) => {
      capsule(0.075, 0.42, PANTS, 1.5, 1.1, z, g).rotation.z = Math.PI / 2 - 0.1;
      capsule(0.065, 0.4, PANTS, 1.85, 0.87, z, g).rotation.z = 0.56;
      box(0.26, 0.1, 0.12, 0x22211f, 2.0, 0.62, z, g);
    });
    [-0.53, -0.37].forEach((z) => { mesh(new THREE.SphereGeometry(0.05, 10, 8), 0xc79a5c, 1.55, 1.2, z, g).scale.set(1.3, 0.7, 1); });
    // what lives on the dash: every delivery note since March, yesterday's coffee, the folding rule
    box(0.24, 0.025, 0.19, 0xf2f0ea, 2.62, 1.41, 0.42, g).rotation.y = 0.2;
    box(0.22, 0.02, 0.17, 0xffe9a8, 2.6, 1.435, 0.4, g).rotation.y = -0.1;
    cyl(0.035, 0.03, 0.1, 0xf2f0ea, 2.5, 1.44, 0.12, g, 12);
    cyl(0.037, 0.037, 0.012, 0x5a3b22, 2.5, 1.495, 0.12, g, 12);
    box(0.18, 0.03, 0.03, 0xf2b705, 2.66, 1.41, -0.05, g).rotation.y = 0.5;
    // a hi-vis on its hook, the mirror, the pine tree that stopped smelling of pine in 2021
    box(0.02, 0.5, 0.36, 0xd4f53c, 0.8, 1.72, 0.3, g);
    [1.6, 1.8].forEach((y) => box(0.022, 0.035, 0.36, 0xd9dde0, 0.8, y, 0.3, g));
    box(0.03, 0.02, 0.02, 0x1a1b1d, 2.0, 2.27, 0, g);
    box(0.03, 0.07, 0.24, 0x1a1b1d, 1.99, 2.19, 0, g);
    box(0.005, 0.06, 0.22, 0x8aa0b0, 1.973, 2.19, 0, g);
    const fresh = new THREE.Group();
    fresh.position.set(1.99, 2.15, 0.07);
    g.add(fresh);
    box(0.002, 0.08, 0.002, 0xdddddd, 0, -0.04, 0, fresh);
    const tree = mesh(new THREE.ConeGeometry(0.035, 0.09, 3), 0x2e8b3a, 0, -0.12, 0, fresh);
    tree.rotation.y = 0.4; tree.scale.z = 0.25;
    g.userData.fresh = fresh;
    box(0.02, 0.17, 0.55, LINER, 1.99, 2.25, -0.45, g);                             // your sun visor, down
    // the bonnet through the windscreen, and the wipers parked on it
    box(0.52, 0.03, 1.9, 0xe9e7e2, 3.1, 1.22, 0, g).rotation.z = -0.69;
    [-1, 1].forEach((sd) => box(0.03, 0.012, 0.55, 0x111111, 2.86, 1.45, sd * 0.33, g));
  })();
  let snoreAt = 0, inVanWas = false;
  /** The cab while you nap in it: the dark glass out, the clock on the radio, the pine tree swinging, eyes heavy. */
  function updateVanCab() {
    const inVan = gs.waitMode === 'van' && gs.phase !== 'title' && gs.phase !== 'end';
    if (vanCab.visible !== inVan) {
      vanCab.visible = inVan;
      vanWs.visible = vanSideGlass.visible = vanStripe.visible = !inVan;
      document.body.classList.toggle('napping', inVan);
    }
    if (!inVan) return;
    const now = performance.now();
    vanCab.userData.fresh.rotation.x = CALM ? 0 : Math.sin(now / 900) * 0.12;
    const m = Math.floor(gs.t);
    if (m !== vanClock.last) {
      vanClock.last = m;
      const c = vanClock.canvas.getContext('2d');
      c.fillStyle = '#0a0d0b'; c.fillRect(0, 0, 128, 40);
      c.fillStyle = '#5dff8a'; c.font = '700 26px monospace'; c.textAlign = 'center'; c.textBaseline = 'middle';
      c.fillText(clock(gs.t), 64, 21);
      vanClock.tex.needsUpdate = true;
    }
    if (now > snoreAt) { snoreAt = now + rnd(3.4, 4.8) * 1000; sfx('snore'); }
  }

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
    // the grille's slats, the indicators, a sun visor, the roof lights, the exhaust stack, the plate,
    // the wipers, the door handles
    [1.15, 1.3, 1.45, 1.6, 1.75].forEach((y) => box(0.05, 0.03, 1.4, 0x5a5f64, -4.79, y, 0, parent));
    [-1, 1].forEach((sd) => {
      box(0.05, 0.09, 0.16, 0xff9a1a, -4.92, 0.98, sd * 1.05, parent);
      box(0.3, 0.05, 0.12, 0x2a2d31, -3.3, 1.9, sd * 1.16, parent);
      box(0.04, 0.012, 0.75, 0x111111, -4.64, 2.2, sd * 0.45, parent).rotation.x = sd * 0.1;
    });
    box(0.4, 0.06, 2.35, color, -4.5, 3.26, 0, parent);
    [-0.6, 0, 0.6].forEach((z) => box(0.08, 0.06, 0.14, new THREE.MeshBasicMaterial({ color: 0xffa640 }), -4.3, 3.3, z, parent));
    cyl(0.07, 0.07, 1.7, 0x9ea3a8, -2.25, 3.05, 1.02, parent, 10);
    cyl(0.09, 0.07, 0.12, 0x5a5f64, -2.25, 3.95, 1.02, parent, 10);
    sign(['XYZ-789'], 0.52, 0.12, '#f4f4f0', '#111', parent, -4.93, 0.7, 0, { border: '#111' }).rotation.y = -Math.PI / 2;
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
  // the control panel at the back, lit; the grate over the hopper; the firm's name; the beacons
  box(0.12, 0.55, 0.4, 0xd8dcdf, 3.05, 1.7, -0.95, pump);
  [[0x3ec16a, 1.85], [0xff3b30, 1.72], [0xf2b705, 1.59]].forEach(([c2, y]) => box(0.02, 0.06, 0.06, new THREE.MeshBasicMaterial({ color: c2 }), 3.0, y, -0.85, pump));
  cyl(0.035, 0.035, 0.02, 0xd02020, 2.99, 1.5, -1.05, pump, 10).rotation.z = Math.PI / 2;
  for (let k = -2; k <= 2; k++) box(0.03, 0.03, 1.2, 0x2a2d31, 3.8 + k * 0.18, 2.14, 0, pump);
  // (the far side has the pipe rack over it; the name goes on this one)
  sign(['CONCRETE PUMPING', 'PUMP HIRE · 24 H'], 3.2, 0.7, null, '#1d1f22', pump, 0.6, 1.95, -1.162).rotation.y = Math.PI;
  cyl(0.09, 0.1, 0.12, new THREE.MeshBasicMaterial({ color: 0xff8a1a }), -3.4, 3.4, 0.7, pump, 10);
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
  // steel bands round the drum where the rollers run
  [[-0.4, 1.19], [0.7, 1.1]].forEach(([x, r]) => { mesh(new THREE.TorusGeometry(r, 0.035, 6, 30), 0x5a5f64, x, 0, 0, drumSpin).rotation.y = Math.PI / 2; });
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
  // the ladder up the back to the hopper, the rear lights, the mudflaps, the beacon
  [-0.25, 0.25].forEach((z) => box(0.05, 2.1, 0.05, 0x9ea3a8, 3.45, 2.15, z + 0.45, mixer));
  for (let y = 1.25; y < 3.2; y += 0.3) box(0.04, 0.035, 0.5, 0x9ea3a8, 3.45, y, 0.45, mixer);
  [-1, 1].forEach((sd) => {
    box(0.05, 0.14, 0.28, 0xb3261e, 3.72, 1.0, sd * 1.0, mixer);
    box(0.02, 0.45, 0.4, 0x1d1f22, 3.72, 0.62, sd * 1.1, mixer);
  });
  cyl(0.09, 0.1, 0.12, new THREE.MeshBasicMaterial({ color: 0xff8a1a }), -3.4, 3.4, 0.7, mixer, 10);
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
  // The ConWiC logo, from the app's own artwork (res/values/logo_paths.xml): the badge, a
  // brown block with the mark knocked out of it, over the CONWIC / FLOORS FOR LIVING wordmark.
  // Printed on the back of the crew's vests.
  const LOGO = {
    block: 'M0,0H406V246H0Z',
    mark: 'M79.5,42H178.5L178,81.5L81.5,82L82,165.5H262L263.5,164L264,41.5L302.5,42V164L304,165.5H325L325.5,145L326,41.5L365.5,42V168L364.5,169V174L363.5,175L362.5,181L360.5,183L358.5,188L354.5,192V193L348,198.5L345,199.5L341,202.5L335,203.5L334,204.5H328L327,205.5H80L79,204.5H73L69,202.5H66L59,198.5L56,195.5H55L47.5,187L44.5,181V179L42.5,175V172L41.5,171V75L42.5,74L43.5,68L47.5,60L55,51.5H56L59,48.5L66,44.5H68L72,42.5ZM200.5,42H240.5V140H200.5Z',
    word: 'M93.5,1L144,0.5L145,1.5L159,2.5L177,6.5L189,10.5L209,21.5L218.5,30L226.5,40L234.5,58L236.5,67L236,69.5H184L182.5,64L177.5,56L172,50.5L163,44.5L149,39.5L139,38.5L138,37.5H125L124,36.5L100,37.5L80,42.5L67,49.5L57.5,60L53.5,68L51.5,76V100L56.5,115L61.5,122L70,129.5L80,134.5L94,138.5H100L101,139.5H136L152,136.5L169,128.5L179.5,118L184,107.5H236L234.5,118L227.5,135L216,149.5L203,159.5L191,165.5L169,172.5L152,174.5L151,175.5H142L141,176.5H97L96,175.5H87L86,174.5L74,173.5L58,169.5L47,165.5L28,154.5L21.5,149L11.5,137L5.5,125L0.5,104V74L1.5,73V67L5.5,52L11.5,40L16.5,33L29,21.5L49,10.5L68,4.5ZM346.5,1L392,0.5L393,1.5H402L403,2.5H410L420,4.5L442,11.5L458,20.5L472.5,34L481.5,48L486.5,63L487.5,79L488.5,80L487.5,107L482.5,126L473.5,142L458,156.5L442,165.5L424,171.5L409,174.5H403L402,175.5H393L392,176.5H348L347,175.5H338L337,174.5H330L320,172.5L300,166.5L280,155.5L267.5,144L259.5,132L254.5,120L251.5,105V72L252.5,71L253.5,61L257.5,49L267.5,33L281,20.5L300,10.5L320,4.5ZM1316.5,1L1367,0.5L1368,1.5L1382,2.5L1400,6.5L1412,10.5L1432,21.5L1441.5,30L1449.5,40L1457.5,58L1459.5,67L1459,69.5H1407L1405.5,64L1400.5,56L1395,50.5L1386,44.5L1372,39.5L1362,38.5L1361,37.5H1347L1346,36.5L1323,37.5L1303,42.5L1290,49.5L1280.5,60L1276.5,68L1274.5,76V100L1279.5,115L1284.5,122L1293,129.5L1303,134.5L1317,138.5H1323L1324,139.5H1359L1375,136.5L1392,128.5L1402.5,118L1407,107.5H1459L1457.5,118L1450.5,135L1439,149.5L1426,159.5L1414,165.5L1392,172.5L1375,174.5L1374,175.5H1365L1364,176.5H1320L1319,175.5H1310L1309,174.5L1297,173.5L1281,169.5L1270,165.5L1255,157.5L1245,149.5L1234.5,137L1228.5,125L1223.5,104V74L1224.5,73V67L1228.5,52L1234.5,40L1239.5,33L1252,21.5L1272,10.5L1291,4.5ZM508.5,5L594,4.5L695,121.5L695.5,5L747,4.5L747.5,172L731,172.5H680L560,38.5L559.5,172L509,172.5ZM756.5,5H814.5L867,136.5L868.5,135L877.5,110L920,4.5H987L988.5,6L1040,136.5L1042.5,133L1054.5,103L1055.5,98L1071.5,60L1072.5,55L1092.5,5L1150,4.5L1150.5,6L1145.5,16L1077,172.5H1004L997.5,160L994.5,151L986.5,135L983.5,126L979.5,119L954,59.5L950.5,65L947.5,74L943.5,81L940.5,90L936.5,97L933.5,106L929.5,113L904,172.5H830ZM1156.5,5H1207.5V172H1156.5ZM352,37.5L351,38.5L340,39.5L321,47.5L310.5,57L306.5,63L302.5,75V101L304.5,109L310.5,120L318,127.5L330,134.5L351,139.5H366L367,140.5L387,139.5L400,137.5L417,130.5L431.5,116L434.5,110L437.5,98V80L434.5,67L425,52.5L417,46.5L409,42.5L388,37.5ZM0.5,216L1,215.5H35L35.5,216V221L35,221.5H10L8.5,223V228L9,228.5H21H32L32.5,229V234L32,234.5H9L8.5,235V244L8,244.5H1L0.5,244ZM61.5,216L62,215.5H69L69.5,216V238L70,238.5H96L96.5,239V244L96,244.5H62L61.5,244ZM131.5,216L132,215.5H147L148,216.5H151L152,217.5H153L154,218.5H155L157.5,221V222L159.5,225V235L158.5,236V238L154,242.5H153L152,243.5H150L149,244.5H147H142L141,245.5H139L138,244.5H131L130,243.5H128L127,242.5H126L120.5,237V235L119.5,234V227L120.5,226V224L121.5,223V222L125,218.5H126L129,216.5H131ZM196.5,216L197,215.5H213L214,216.5H216L217,217.5H218L219,218.5H220L223.5,222V223L224.5,224V226L225.5,227V232L224.5,233V236L223.5,237V238L219,242.5H218L217,243.5H215L214,244.5H207L206,245.5H204L203,244.5H196L195,243.5H193L192,242.5H191L186.5,238V237L185.5,236V225L186.5,224V223L187.5,222V221L190,218.5H191L194,216.5H196ZM251.5,216L252,215.5H279L280,216.5H283L284,217.5H285L287.5,220V221L289.5,224V227L288.5,228V230L287.5,231V232L284,235.5H283L282.5,236L284.5,238V239L288.5,243V244L288,244.5H287H280L279.5,244V243L277.5,241V240L274,236.5H260L259.5,237V244L259,244.5H251L250.5,244V217ZM324.5,216L325,215.5H344L345,216.5H348L349,217.5H350L353.5,221V223L353,223.5H345L342,220.5H327L326,221.5H325L323.5,223V226L324,226.5H326L327,227.5H337L338,228.5H347L348,229.5H350L353.5,233V234L354.5,235V236L353.5,237V239L350,242.5H349L348,243.5H346L345,244.5H342H338L337,245.5H333L332,244.5H323L322,243.5H320L319,242.5H318L314.5,239V237L315,236.5H323L326,239.5H328L329,240.5H338L339,239.5H342L345.5,236L344.5,235V234L344,233.5H341L340,232.5H327L326,231.5H321L320,230.5H318L314.5,227V222L315.5,221V220L317,218.5H318L321,216.5H324ZM420.5,216L421,215.5H455L456.5,217V221L456,221.5H430L428.5,223V228L429,228.5H440H452L452.5,229V234L452,234.5H430L428.5,236V244L428,244.5H421L420.5,244ZM490.5,216L491,215.5H506L507,216.5H510L511,217.5H512L513,218.5H514L516.5,221V222L518.5,224V235L517.5,236V238L513,242.5H512L511,243.5H509L508,244.5H506H501L500,245.5H498L497,244.5H490L489,243.5H487L486,242.5H485L480.5,238V237L479.5,236V233L478.5,232V229L479.5,228V224L480.5,223V222L484,218.5H485L488,216.5H490ZM544.5,216L545,215.5H573L574,216.5H577L579,218.5H580L580.5,219V220L582.5,222V230L581.5,231V232L579,234.5H578L576.5,236L577.5,237V238L581.5,242V243L582.5,244L582,244.5H581H574L573.5,244V243L570.5,240V239L568,236.5H554L553.5,237V244L553,244.5H545L544.5,244ZM649.5,216L650,215.5H657L657.5,216V237L659,238.5H684L684.5,239V244L684,244.5H650L649.5,244ZM710.5,216L711,215.5H718L718.5,216V244L718,244.5H711L710.5,244ZM742.5,216L743,215.5H751L751.5,216V217L752.5,218V219L753.5,220V221L754.5,222V223L755.5,224V225L756.5,226V227L757.5,228V229L759.5,232V234L760.5,235V236L761.5,237V238L763,239.5L763.5,239V238L764.5,237V236L765.5,235V234L766.5,233V232L767.5,231V229L768.5,228V227L769.5,226V225L770.5,224V223L771.5,222V221L772.5,220V219L773.5,218V217L775,215.5H783L783.5,216V217L782.5,218V219L780.5,221V222L779.5,223V224L778.5,225V226L777.5,227V228L776.5,229V230L775.5,231V232L774.5,233V234L773.5,235V236L772.5,237V238L771.5,239V240L770.5,241V242L769.5,243V244L769,244.5H757L755.5,243V242L754.5,241V240L753.5,239V238L752.5,237V236L751.5,235V234L750.5,233V232L749.5,231V230L748.5,229V228L747.5,227V226L746.5,225V224L745.5,223V222L744.5,221V220L742.5,218ZM806.5,216L807,215.5H815L815.5,216V244L815,244.5H807L806.5,244ZM843.5,216L844,215.5H857L859.5,218V219L865.5,225V226L871.5,232V233L874,235.5L874.5,235V217L876,215.5H883L883.5,216V244L883,244.5H880H872L868.5,241V240L860.5,232V231L852.5,223V222L852,221.5L851.5,222V244L851,244.5H843L842.5,244V217ZM921.5,216L922,215.5H937L938,216.5H940L941,217.5H943L948.5,223V225L948,225.5H940L937,222.5H936L935,221.5H924L923,222.5H922L917.5,227V233L918.5,234V235L921,237.5H922L923,238.5H926L927,239.5H931L932,238.5H935L936,237.5H937L940.5,234L940,233.5H931H930L929.5,233V230L930,229.5H948L948.5,230V244L948,244.5H945L944.5,244V241L944,240.5H943L941,242.5H940L937,244.5H931L930,245.5H928L927,244.5H920L919,243.5H917L915,241.5H914L911.5,239V238L909.5,235V225L910.5,224V223L911.5,222V221L914,218.5H915L918,216.5H921ZM134,221.5L133,222.5H132L128.5,226V234L132,237.5H133L134,238.5H137L138,239.5H142L143,238.5H145H146L147,237.5H148L150.5,235V234L151.5,233V227L149.5,225V224L148,222.5H146L145,221.5ZM200,221.5L199,222.5H197L196.5,223V224L193.5,227V233L194.5,234V235L197,237.5H198L199,238.5H202L203,239.5H207L208,238.5H209H211L212,237.5H213L216.5,234V226L213,222.5H212L211,221.5ZM260,221.5L259.5,222V230L260,230.5H278L279,229.5L280.5,228V224L279,222.5H278L277,221.5ZM494,221.5L493,222.5H491L487.5,226V234L491,237.5H492L493,238.5H496L497,239.5H501L502,238.5H503H505L506,237.5H507L509.5,235V234L510.5,233V227L509.5,226V225L507,222.5H505L504,221.5ZM554,221.5L553.5,222V230L554,230.5H571L572,229.5H573L574.5,228V225L573.5,224V223L573,222.5H571L570,221.5Z',
  };
  let logoMatMade = null;
  function logoMat() {
    if (logoMatMade) return logoMatMade;
    const c = document.createElement('canvas');
    c.width = 512; c.height = 360;
    const g = c.getContext('2d');
    // the badge, 260 wide, over the wordmark, 420 wide, the pair centred
    const bw = 260, bh = bw * 246 / 406, ww = 420, wh = ww * 245 / 1459, top = (360 - bh - 20 - wh) / 2;
    g.save();
    g.translate((512 - bw) / 2, top);
    g.scale(bw / 406, bw / 406);
    g.fillStyle = '#704727';
    g.fill(new Path2D(LOGO.block));
    g.fillStyle = '#ffffff';
    g.fill(new Path2D(LOGO.mark), 'evenodd');
    g.restore();
    g.save();
    g.translate((512 - ww) / 2, top + bh + 20);
    g.scale(ww / 1459, ww / 1459);
    g.fillStyle = '#2a1c10';
    g.fill(new Path2D(LOGO.word), 'evenodd');
    g.restore();
    const tex = new THREE.CanvasTexture(c);
    tex.encoding = THREE.sRGBEncoding;
    tex.anisotropy = 4;
    logoMatMade = new THREE.MeshLambertMaterial({ map: tex, alphaTest: 0.5 });
    return logoMatMade;
  }
  function makePerson(opts) {
    opts = opts || {};
    const g = new THREE.Group();
    const body = new THREE.Group();
    g.add(body);
    const shirt = opts.shirt || pick([0x3b5b8c, 0x8c3b3b, 0x4e7a44, 0x5a4f7a, 0xc9c3b8, 0x2f3540, 0x7a5a3a]);
    const pants = opts.pants || pick([0x2f3540, 0x3a3226, 0x23262b, 0x4a5a70, 0x5b5a52]);
    const skin = opts.skin || pick(SKINS);
    const hairCol = pick(HAIR);
    const worker = !!opts.vest;
    const leg = (x) => {
      const p = new THREE.Group();
      p.position.set(x, 0.9, 0);
      capsule(0.075, 0.62, pants, 0, -0.42, 0, p);
      box(0.12, 0.08, 0.27, 0x22211f, 0, -0.86, 0.04, p);
      box(0.13, 0.025, 0.29, 0x121212, 0, -0.895, 0.045, p);                         // the sole
      box(0.115, 0.05, 0.07, 0x3a352f, 0, -0.84, 0.15, p);                           // the toecap
      if (worker) box(0.1, 0.11, 0.03, 0x1f2124, 0, -0.46, 0.07, p);                  // a knee pad
      g.add(p);
      return p;
    };
    const legL = leg(-0.1), legR = leg(0.1);
    // concrete on the boots, for whoever walks through the pour
    const bootCrust = [legL, legR].map((p) => { const c = box(0.135, 0.06, 0.2, 0x9b9d9a, 0, -0.84, 0.09, p); c.visible = false; return c; });
    box(0.34, 0.14, 0.2, pants, 0, 0.93, 0, body);
    const torso = cyl(0.2, 0.16, 0.6, shirt, 0, 1.24, 0, body, 12);
    torso.scale.z = 0.65;
    // a belt with a buckle, a collar at the neck
    cyl(0.168, 0.166, 0.045, 0x2a2520, 0, 0.98, 0, body, 12).scale.z = 0.68;
    box(0.05, 0.035, 0.02, 0xb9bec3, 0, 0.98, 0.114, body);
    mesh(new THREE.TorusGeometry(0.062, 0.02, 6, 14), new THREE.Color(shirt).multiplyScalar(0.8).getHex(), 0, 1.57, 0, body).rotation.x = Math.PI / 2;
    if (opts.vest) {
      const v = cyl(0.206, 0.166, 0.46, opts.vest, 0, 1.22, 0, body, 12);
      v.scale.z = 0.68;
      // a company vest has its stripes low, to leave the back for the logo
      const vr = (y) => 0.166 + ((y - 0.99) / 0.46) * 0.04;
      (opts.logo ? [1.05, 1.14] : [1.12, 1.3]).forEach((y) => {
        const st = cyl(opts.logo ? vr(y + 0.0175) + 0.006 : 0.209, opts.logo ? vr(y - 0.0175) + 0.006 : 0.19, 0.035, 0xd9dde0, 0, y, 0, body, 12);
        st.scale.z = 0.69;
      });
      // two pockets on the front, a shade darker
      [-1, 1].forEach((sd) => box(0.07, 0.06, 0.012, new THREE.Color(opts.vest).multiplyScalar(0.82).getHex(), sd * 0.08, 1.1, 0.13, body));
      if (opts.logo) {
        // the back is -z; a strip of the vest's own curve, 110 degrees round, just proud of it
        const arc = 1.92;
        const patch = mesh(new THREE.CylinderGeometry(vr(1.43) + 0.004, vr(1.17) + 0.004, 0.26, 16, 1, true, Math.PI - arc / 2, arc), logoMat(), 0, 1.3, 0, body);
        patch.scale.z = 0.69;
      }
    }
    const sh = mesh(new THREE.SphereGeometry(0.21, 12, 8), opts.vest || shirt, 0, 1.5, 0, body);
    sh.scale.set(1, 0.42, 0.62);
    const arm = (x) => {
      const p = new THREE.Group();
      p.position.set(x, 1.5, 0);
      capsule(0.055, 0.46, shirt, 0, -0.28, 0, p);
      // workers' hands are in gloves; everybody else's are hands
      mesh(new THREE.SphereGeometry(0.055, 8, 6), worker ? 0xc79a5c : skin, 0, -0.6, 0, p).scale.set(1, 1.15, 0.8);
      if (worker) cyl(0.05, 0.05, 0.05, 0x8a6a3a, 0, -0.54, 0, p, 8);
      // the middle finger, for answering back: turned to point up whatever the arm is doing
      const f = new THREE.Group();
      f.position.set(0, -0.6, 0);
      capsule(0.017, 0.07, skin, 0, 0.07, 0, f);
      f.visible = false;
      p.add(f);
      p.userData.finger = f;
      body.add(p);
      return p;
    };
    const armL = arm(-0.25), armR = arm(0.25);
    cyl(0.05, 0.055, 0.1, skin, 0, 1.6, 0, body, 8);
    const head = new THREE.Group();
    head.position.set(0, 1.74, 0);
    body.add(head);
    mesh(new THREE.SphereGeometry(0.12, 14, 12), skin, 0, 0, 0, head).scale.set(0.92, 1.05, 1);
    // a face: eyes, brows, a nose, a mouth, ears — and on some a beard or a pair of glasses
    [-1, 1].forEach((s) => {
      box(0.022, 0.022, 0.01, 0x111111, s * 0.04, 0.02, 0.112, head);
      box(0.036, 0.009, 0.012, hairCol, s * 0.042, 0.05, 0.108, head).rotation.z = -s * 0.12;
      mesh(new THREE.SphereGeometry(0.028, 8, 6), skin, s * 0.112, 0.0, -0.005, head).scale.set(0.45, 1, 0.8);
    });
    box(0.022, 0.035, 0.03, skin, 0, -0.012, 0.12, head);
    box(0.042, 0.008, 0.01, 0x7a3b30, 0, -0.05, 0.109, head);
    if (opts.g !== 'f' && opts.g !== 'kid' && chance(0.35)) {
      mesh(new THREE.SphereGeometry(0.118, 12, 6, 0, Math.PI * 2, Math.PI * 0.56, Math.PI * 0.44), hairCol, 0, 0.006, 0.004, head).scale.set(0.95, 1.05, 1.02);
      box(0.05, 0.012, 0.012, hairCol, 0, -0.032, 0.114, head);                     // the moustache
    }
    if (chance(0.15)) {
      [-1, 1].forEach((s) => mesh(new THREE.TorusGeometry(0.02, 0.004, 4, 12), 0x1d1f22, s * 0.042, 0.02, 0.118, head));
      box(0.025, 0.004, 0.004, 0x1d1f22, 0, 0.024, 0.12, head);
    }
    const hat = opts.hat === undefined ? pick(['hard', 'cap', 'hair', 'hair']) : opts.hat;
    if (hat === 'hard') {
      const hc = opts.hatColor || pick([0xf2b705, 0xf2f0ea, 0xff6b1a, 0x2c6ad6]);
      mesh(new THREE.SphereGeometry(0.138, 14, 8, 0, Math.PI * 2, 0, Math.PI / 2), hc, 0, 0.03, 0, head);
      cyl(0.165, 0.165, 0.014, hc, 0, 0.035, 0.025, head, 18);
      box(0.02, 0.012, 0.25, hc, 0, 0.165, -0.01, head);                              // the ridge over the top
      box(0.05, 0.03, 0.012, 0x1d1f22, 0, 0.07, 0.13, head);                          // the badge on the front
    } else if (hat === 'cap') {
      const cc = pick([0x2f3540, 0x8c3b3b, 0x4e7a44, 0xc9c3b8]);
      mesh(new THREE.SphereGeometry(0.128, 14, 8, 0, Math.PI * 2, 0, Math.PI / 2), cc, 0, 0.02, 0, head);
      box(0.15, 0.012, 0.1, cc, 0, 0.03, 0.14, head);
    } else {
      mesh(new THREE.SphereGeometry(0.126, 12, 8, 0, Math.PI * 2, 0, Math.PI / 2.1), hairCol, 0, 0.015, -0.012, head);
    }
    // a ponytail, under whatever is on top
    if (opts.g === 'f') capsule(0.042, 0.15, hairCol, 0, -0.07, -0.125, head).rotation.x = 0.35;
    g.userData = { legL, legR, armL, armR, head, body, bootCrust, phase: Math.random() * 6 };
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
    const light = new THREE.Color(c).lerp(new THREE.Color(0xf2ece0), 0.55).getHex();
    const floppy = !cat && chance(0.5);
    if (cat) {
      mesh(new THREE.SphereGeometry(0.02, 6, 6), 0xd98a9a, 0.12, -0.01, 0, head);
      // whiskers, three a side
      [-1, 1].forEach((sd) => [0, 1, 2].forEach((k) => { box(0.002, 0.002, 0.09, 0xf2f0ea, 0.115, -0.02 + k * 0.009, sd * 0.06, head).rotation.y = sd * (0.2 + k * 0.12); }));
    } else {
      capsule(0.045, 0.08, chance(0.4) ? light : c, 0.1, -0.03, 0, head).rotation.z = Math.PI / 2;
      mesh(new THREE.SphereGeometry(0.025, 6, 6), 0x111111, 0.175, -0.02, 0, head);
      if (chance(0.4)) box(0.05, 0.008, 0.028, 0xe07a8a, 0.15, -0.075, 0, head).rotation.z = -0.5;   // the tongue out
    }
    [-1, 1].forEach((s) => {
      if (floppy) {
        const ear = mesh(new THREE.SphereGeometry(0.05, 8, 6), c, -0.02, 0.02, s * 0.088, head);
        ear.scale.set(0.55, 1.25, 0.25); ear.rotation.x = s * 0.25;
      } else {
        const ear = mesh(new THREE.ConeGeometry(cat ? 0.05 : 0.035, cat ? 0.12 : 0.09, cat ? 4 : 6), c, -0.02, cat ? 0.11 : 0.1, s * (cat ? 0.07 : 0.06), head);
        ear.rotation.x = s * (cat ? 0.25 : 0.35);
      }
      // the eyes: a cat's green with a slit, a dog's dark with a glint
      mesh(new THREE.SphereGeometry(cat ? 0.02 : 0.017, 8, 6), cat ? 0x9bd14a : 0x1a1410, cat ? 0.098 : 0.074, 0.03, s * 0.045, head);
      if (cat) box(0.004, 0.024, 0.005, 0x111111, 0.117, 0.03, s * 0.045, head);
      else mesh(new THREE.SphereGeometry(0.005, 4, 4), 0xffffff, 0.088, 0.037, s * 0.043, head);
    });
    // a collar, and on most a tag
    const collar = mesh(new THREE.TorusGeometry(cat ? 0.06 : 0.075, 0.012, 6, 16), pick([0xd83a2e, 0x2c6ad6, 0x2e8b3a, 0xf2b705]), 0.29, 0.6, 0, body);
    collar.quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), new THREE.Vector3(0.78, 0.62, 0));
    cyl(0.018, 0.018, 0.006, cat ? 0xd9c24a : 0xb9bec3, 0.33, 0.52, 0, body, 8).rotation.x = Math.PI / 2;
    // patches on some dogs, stripes on a tabby, a white bib on others
    if (!cat && chance(0.4)) [[-0.1, 0.55, 0.1], [0.08, 0.5, -0.11], [-0.22, 0.47, -0.05]].forEach(([x, y, z]) => mesh(new THREE.SphereGeometry(0.07, 8, 6), pick([0xf2ece0, 0x2b2622, 0x6b4a2b]), x, y, z, body).scale.set(1.2, 0.8, 0.35));
    if (cat && chance(0.45)) [-0.15, -0.05, 0.05, 0.15].forEach((x) => mesh(new THREE.TorusGeometry(0.13, 0.012, 4, 14), new THREE.Color(c).multiplyScalar(0.6).getHex(), x, 0.45, 0, body).rotation.y = Math.PI / 2);
    if (chance(0.35)) mesh(new THREE.SphereGeometry(0.075, 8, 6), 0xf2ece0, 0.26, 0.47, 0, body).scale.set(0.6, 1, 0.9);
    const tail = new THREE.Group();
    tail.position.set(-0.3, 0.52, 0);
    body.add(tail);
    capsule(0.025, cat ? 0.42 : 0.2, c, 0, cat ? 0.22 : 0.12, 0, tail);
    tail.rotation.z = cat ? 0.15 : 0.7;
    const socks = chance(0.3);
    const legs = [[0.2, 0.08], [0.2, -0.08], [-0.2, 0.08], [-0.2, -0.08]].map(([x, z]) => {
      const p = new THREE.Group();
      p.position.set(x, 0.38, z);
      capsule(0.035, 0.26, c, 0, -0.19, 0, p);
      mesh(new THREE.SphereGeometry(0.042, 8, 6), socks ? 0xf2ece0 : c, 0.015, -0.34, 0, p).scale.set(1.3, 0.7, 1);   // the paw
      body.add(p);
      return p;
    });
    g.userData = { legs, tail, head, body, phase: 0, size };
    return g;
  }
  const pumpGuy = makePerson({ shirt: 0x2f3540, vest: 0xff7a1a, hat: 'hard', hatColor: 0xf2f0ea });
  pumpGuy.visible = false;
  scene.add(pumpGuy);
  // the pump's radio remote: a yellow box on a harness at his belly, two sticks, a red stop and an
  // aerial — he works the pump and the boom from it wherever he stands
  {
    const b = pumpGuy.userData.body, r = new THREE.Group();
    r.position.set(0, 1.1, 0.2);
    b.add(r);
    box(0.3, 0.12, 0.13, 0xf2b705, 0, 0, 0, r);
    box(0.27, 0.012, 0.1, 0x2a2a2a, 0, 0.066, 0, r);
    [-0.08, 0.08].forEach((x) => {
      cyl(0.009, 0.011, 0.07, 0x1a1a1a, x, 0.1, 0, r, 6);
      mesh(new THREE.SphereGeometry(0.022, 8, 6), 0x1a1a1a, x, 0.14, 0, r);
    });
    cyl(0.022, 0.022, 0.02, 0xd02020, 0, 0.078, 0.02, r, 10);
    cyl(0.007, 0.007, 0.2, 0x1a1a1a, 0.13, 0.15, -0.03, r, 6);
    box(0.02, 0.012, 0.012, 0x44ff66, -0.11, 0.07, 0.04, r);
    // the harness, up over both shoulders
    [-0.12, 0.12].forEach((x) => { const st = box(0.035, 0.42, 0.012, 0x1f1f1f, x, 0.26, -0.07, r); st.rotation.x = -0.28; });
    pumpGuy.userData.remote = r;
  }
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
    // a second tier on every pine, a second clump on every leafy tree
    const pine2 = new THREE.InstancedMesh(pineGeo, new THREE.MeshLambertMaterial({ color: 0xffffff }), pines.length);
    const leaf2 = new THREE.InstancedMesh(leafGeo, new THREE.MeshLambertMaterial({ color: 0xffffff }), leafy.length);
    pines.forEach(([x, z, s], k) => {
      m4.compose(ps.set(x, 5.4 * s, z), q.identity(), sc.set(1.5 * s, 5.6 * s, 1.5 * s)); pine2.setMatrixAt(k, m4);
      pine2.setColorAt(k, col.setHSL(0.34 + rnd(-0.03, 0.03), 0.36, rnd(0.19, 0.27)));
    });
    leafy.forEach(([x, z, s], k) => {
      m4.compose(ps.set(x + rnd(-1, 1) * s, 5.6 * s, z + rnd(-1, 1) * s), q.setFromEuler(new THREE.Euler(rnd(0, 3), rnd(0, 3), 0)), sc.set(1.8 * s, 1.9 * s, 1.8 * s)); leaf2.setMatrixAt(k, m4);
      leaf2.setColorAt(k, col.setHSL(0.25 + rnd(-0.04, 0.05), 0.45, rnd(0.28, 0.38)));
    });
    [pine2, leaf2].forEach((m) => { m.castShadow = true; scene.add(m); });
    // tufts of grass along the fence, outside it and in the corners of the yard
    const tufts = [];
    for (let k = 0; k < 2000 && tufts.length < 520; k++) {
      const x = rnd(-75, 75), z = rnd(-60, 60);
      const outside = Math.abs(x) > 44.5 || Math.abs(z) > 34.5, corner = Math.abs(x) > 39 || Math.abs(z) > 29;
      if (!(outside || corner) || Math.abs(z - 44) < 6 || (Math.abs(x - 50) < 6 && z > -20)) continue;
      tufts.push([x, z]);
    }
    const tuftM = new THREE.InstancedMesh(new THREE.ConeGeometry(0.14, 0.4, 4).translate(0, 0.2, 0), new THREE.MeshLambertMaterial({ color: 0xffffff }), tufts.length);
    tufts.forEach(([x, z], k) => {
      m4.compose(ps.set(x, 0, z), q.setFromEuler(new THREE.Euler(rnd(-0.2, 0.2), rnd(0, 3), rnd(-0.2, 0.2))), sc.setScalar(rnd(0.6, 1.5)));
      tuftM.setMatrixAt(k, m4);
      tuftM.setColorAt(k, col.setHSL(0.22 + rnd(-0.03, 0.05), 0.4, rnd(0.22, 0.36)));
    });
    scene.add(tuftM);
    // the road: a dashed line down the middle, kerbs, a pavement, lamp posts; a few parked cars
    const dash = new THREE.InstancedMesh(new THREE.BoxGeometry(3, 0.02, 0.15), lam(0xe8e6df), 120);
    for (let k = 0; k < 100; k++) { m4.makeTranslation(-400 + k * 8, 0.025, 44); dash.setMatrixAt(k, m4); }
    for (let k = 0; k < 20; k++) { m4.makeRotationY(Math.PI / 2).setPosition(50, 0.025, -18 + k * 3.2); dash.setMatrixAt(100 + k, m4); }
    scene.add(dash);
    [39.4, 48.6].forEach((z) => box(900, 0.14, 0.25, 0xb9b5aa, 0, 0.07, z));
    box(900, 0.03, 2.2, 0x8f8f8a, 0, 0.015, 38.1);
    const lampN = 9, poles = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.07, 0.1, 6, 8).translate(0, 3, 0), lam(0x5a5f64), lampN);
    const heads = new THREE.InstancedMesh(new THREE.BoxGeometry(0.8, 0.14, 0.3), lam(0x3a3d42), lampN);
    for (let k = 0; k < lampN; k++) {
      const x = -120 + k * 30;
      m4.makeTranslation(x, 0, 38.6); poles.setMatrixAt(k, m4);
      m4.makeTranslation(x, 6, 39.1); heads.setMatrixAt(k, m4);
    }
    scene.add(poles); scene.add(heads);
    [[-30, 0xa9b8c4], [-8, 0x8c3b3b], [26, 0xe9e2d0], [70, 0x2f3540]].forEach(([x, c2]) => {
      const car = new THREE.Group();
      box(4.2, 0.62, 1.8, c2, 0, 0.62, 0, car);
      box(2.3, 0.55, 1.62, GLASS, -0.2, 1.2, 0, car);
      box(2.1, 0.06, 1.6, c2, -0.2, 1.5, 0, car);
      [-1, 1].forEach((sd) => { box(0.05, 0.14, 0.34, 0xfff3d6, 2.1, 0.72, sd * 0.6, car); box(0.05, 0.14, 0.3, 0xb3261e, -2.1, 0.75, sd * 0.62, car); });
      [[-1.35, 0.8], [-1.35, -0.8], [1.35, 0.8], [1.35, -0.8]].forEach(([wx, wz]) => wheel(car, wx, wz, 0.32, 0.22));
      car.position.set(x, 0, 41.2);
      car.rotation.y = chance(0.5) ? 0 : Math.PI;
      scene.add(car);
    });
  })();
  // clouds, drifting over, the colour of whatever the sky is doing
  const cloudTex = paintTex(128, 64, 1, 1, (g) => {
    for (let k = 0; k < 14; k++) {
      const x = 20 + Math.random() * 88, y = 22 + Math.random() * 22, r = 12 + Math.random() * 18;
      const gr = g.createRadialGradient(x, y, 0, x, y, r);
      gr.addColorStop(0, 'rgba(255,255,255,0.9)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = gr; g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.fill();
    }
  });
  cloudTex.wrapS = cloudTex.wrapT = THREE.ClampToEdgeWrapping;
  const cloudMat = new THREE.SpriteMaterial({ map: cloudTex, transparent: true, depthWrite: false, fog: false, opacity: 0.85 });
  const clouds = Array.from({ length: 11 }, () => {
    const c = new THREE.Sprite(cloudMat);
    c.position.set(rnd(-260, 260), rnd(75, 120), rnd(-260, 260));
    const w = rnd(70, 130);
    c.scale.set(w, w * 0.45, 1);
    scene.add(c);
    return c;
  });
  function updateClouds(dayness) {
    const drift = performance.now() / 1000 * 1.1;
    clouds.forEach((c, k) => { c.position.x = ((c.userData.x0 === undefined ? (c.userData.x0 = c.position.x) : c.userData.x0) + drift + 300) % 600 - 300; void k; });
    cloudMat.color.setRGB(0.25 + 0.75 * dayness, 0.27 + 0.73 * dayness, 0.32 + 0.68 * dayness);
    cloudMat.opacity = day && day.rh > 80 ? 0.95 : 0.8;
  }
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
  // nearer in: houses with pitched roofs, all in two instanced meshes, and a church spire
  (function houses() {
    const spots = [];
    for (let k = 0; k < 600 && spots.length < 30; k++) {
      const a = rnd(-Math.PI, Math.PI), r = rnd(56, 120);
      const x = Math.cos(a) * r * 1.15, z = Math.sin(a) * r;
      if (Math.abs(z - 44) < 11 || (Math.abs(x - 50) < 11 && z > -22 && z < 44)) continue;   // the roads
      if (spots.some((q) => hyp(q[0], q[1], x, z) < 15)) continue;
      spots.push([x, z, rnd(7, 12), rnd(6, 9), rnd(3.2, 6.5), rnd(0, Math.PI), rnd(2.2, 4)]);
    }
    const wallGeo = new THREE.BoxGeometry(1, 1, 1); wallGeo.translate(0, 0.5, 0);
    const roofGeo = new THREE.CylinderGeometry(0.5, 0.5, 1, 3); roofGeo.rotateX(-Math.PI / 2); roofGeo.translate(0, 0.25, 0);
    // a front with windows (white frames, a cross in each) and a door, so a house reads as a house
    const facade = paintTex(128, 64, 1, 1, (g) => {
      g.fillStyle = '#ffffff'; g.fillRect(0, 0, 128, 64);
      [[10, 12], [48, 12], [90, 12], [10, 38], [90, 38]].forEach(([x, y]) => {
        g.fillStyle = '#f7f7f4'; g.fillRect(x - 2, y - 2, 28, 20);
        g.fillStyle = '#35475a'; g.fillRect(x, y, 24, 16);
        g.fillStyle = '#f7f7f4'; g.fillRect(x + 11, y, 2, 16); g.fillRect(x, y + 7, 24, 2);
      });
      g.fillStyle = '#6a4a32'; g.fillRect(54, 36, 18, 28);
      g.fillStyle = '#c9c3b8'; g.fillRect(0, 60, 128, 4);
    });
    const wallMat = new THREE.MeshLambertMaterial({ color: 0xffffff, map: facade, emissive: 0xffffff, emissiveMap: winTex, emissiveIntensity: 0 });
    townMats.push(wallMat);
    const walls = new THREE.InstancedMesh(wallGeo, wallMat, spots.length);
    const roofs = new THREE.InstancedMesh(roofGeo, new THREE.MeshLambertMaterial({ color: 0xffffff }), spots.length);
    const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), sc = new THREE.Vector3(), ps = new THREE.Vector3(), col = new THREE.Color(), up = new THREE.Vector3(0, 1, 0);
    const WALLS = [0xe9e2d0, 0xc9b79c, 0xd9d4c7, 0xa9b8c4, 0xe0c9a6, 0xb65a3c, 0xf0ece2];
    const ROOFS = [0x7a2e22, 0x3b3f45, 0x5a4a3a, 0x8a3a2a, 0x2f4a5a];
    spots.forEach(([x, z, w, d, h, yaw, rh], k) => {
      q.setFromAxisAngle(up, yaw);
      m4.compose(ps.set(x, 0, z), q, sc.set(w, h, d)); walls.setMatrixAt(k, m4); walls.setColorAt(k, col.setHex(pick(WALLS)));
      m4.compose(ps.set(x, h, z), q, sc.set(w * 1.2, rh / 0.75, d * 1.08)); roofs.setMatrixAt(k, m4); roofs.setColorAt(k, col.setHex(pick(ROOFS)));
    });
    [walls, roofs].forEach((m) => { m.castShadow = true; scene.add(m); });
    const chim = new THREE.InstancedMesh(new THREE.BoxGeometry(0.7, 1.8, 0.7), lam(0x8a3a2a), spots.length);
    spots.forEach(([x, z, w, , h, yaw, rh], k) => {
      const [cx, cz] = [Math.cos(yaw) * w * 0.28, -Math.sin(yaw) * w * 0.28];
      m4.compose(ps.set(x + cx, h + rh * 0.55, z + cz), q.setFromAxisAngle(up, yaw), sc.set(1, 1, 1));
      chim.setMatrixAt(k, m4);
    });
    scene.add(chim);
    // the church, on the far side of the town
    const ch = new THREE.Group();
    box(9, 9, 18, 0xe6e0d2, 0, 4.5, 0, ch);
    mesh(new THREE.CylinderGeometry(0.5, 0.5, 1, 3).rotateX(-Math.PI / 2).translate(0, 0.25, 0), 0x7a2e22, 0, 9, 0, ch).scale.set(10.8, 6, 19);
    box(4.5, 16, 4.5, 0xe6e0d2, 0, 8, 10, ch);
    mesh(new THREE.ConeGeometry(3.2, 11, 4), 0x2f4a5a, 0, 21.5, 10, ch).rotation.y = Math.PI / 4;
    ch.position.set(-60, 0, 96);
    ch.rotation.y = 0.4;
    scene.add(ch);
  })();
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
  let surfWait = 0, surfTool = '';
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
  function paintPour(x, z) { netPaint('pour', x, z); disc(x, z, 0.7, speckle, 0.85); }
  /** A float drawn across: straight, overlapping strokes. */
  function paintFloat(x, z, rot) {
    netPaint('float', x, z, rot);
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
    netPaint('pan', x, z, r);
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
    netPaint('blade', x, z, r);
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
    netPaint('fibres', x, z);
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
    netPaint('gouge', x, z, rot);
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
    } else if (m.kind === 'puke') {
      // not a print: a stain, yellow-brown, with bits in it
      let seed = Math.floor((m.x + 20) * 613 + (m.z + 20) * 271);
      const r = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
      g.globalAlpha = Math.min(1, a * 1.2);
      g.fillStyle = '#9a8446';
      ell(0, 0, 26, 18, 0.3);
      for (let k = 0; k < 7; k++) ell(r() * 50 - 25, r() * 36 - 18, 6 + r() * 8, 4 + r() * 6, r() * 3);
      g.fillStyle = '#6d5a2a';
      for (let k = 0; k < 14; k++) ell(r() * 40 - 20, r() * 28 - 14, 1 + r() * 2, 1 + r() * 1.5);
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
  const baseMat = new THREE.MeshLambertMaterial({ map: noiseTex([176, 166, 146], 55, 1) });
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

    // what has to be troweled by hand as a job of its own: every corner and the pipe collars. The
    // straight edges in between are every metre of board, done along the board (see edgeTick).
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
    // Where the laser's height comes from — a painted peg outside the slab with a nail in it — and
    // the corners of the formwork it checks the boards at. Most are near enough; now and then one
    // has been set a centimetre out, and has to be knocked up or down before the pour.
    const corners = site.edges.filter((e) => e.kind === 'corner');
    const picked = [];
    for (const e of corners.sort(() => Math.random() - 0.5)) {
      if (picked.length >= (site.area > 60 ? 4 : 3)) break;
      if (picked.some((q) => hyp(q.x, q.z, e.x, e.z) < 3)) continue;
      picked.push(e);
    }
    const bad = chance(0.55) ? irnd(0, Math.max(0, picked.length - 1)) : -1;
    site.levelChecks = picked.map((e, k) => ({
      x: e.x - e.inX * 0.75, z: e.z - e.inZ * 0.75,
      name: `${e.inZ > 0 ? 'north' : 'south'}-${e.inX > 0 ? 'west' : 'east'} corner`,
      off: k === bad ? Math.round(pick([-1, 1]) * rnd(7, 16)) : Math.round(rnd(-3, 3)),
      done: false, fixed: false,
    }));
    site.bench = P(site.box.x0 - 1.8, site.box.z0 - 1.6);
    {
      const peg = new THREE.Group();
      for (let k = 0; k < 4; k++) cyl(0.035, 0.035, 0.1, k % 2 ? 0xf2f0ea : 0xd8392f, 0, 0.05 + k * 0.1, 0, peg, 8);
      cyl(0.008, 0.008, 0.05, 0x9ea3a8, 0, 0.43, 0, peg, 6);
      peg.position.set(site.bench.x, 0, site.bench.z);
      siteGroup.add(peg);
    }
    site.cuts = edgeCells.filter((c) => !site.ties.some((t) => t.i === c.i && t.j === c.j)).slice(0, site.area > 100 ? 3 : site.area > 25 ? 2 : 1).map((c) => {
      const t = { x: gx(c.i) + 0.5 + rnd(-0.25, 0.25), z: gz(c.j) + 0.5 + rnd(-0.25, 0.25), done: false, i: c.i, j: c.j };
      t.bar = cyl(0.009, 0.009, 0.8, RUST, t.x, ry + 0.4, t.z, siteGroup, 5);
      t.bar.castShadow = true;
      return t;
    });

    site.hoseReach = Math.max(16, ...cellsOn.map((c) => hyp(gx(c.i) + 0.5, gz(c.j) + 0.5, pipeEnd().x, pipeEnd().z))) + 2;
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
    if (!gs || !onSlab(x, z)) return rampY(x, z);
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
    // the ring goes dark in its own colour while the countdown fills over it
    m.ringMat.color.setHex(m.hex);
    if (on) m.ringMat.color.multiplyScalar(0.45);
    if (!on) { m.quarter = 0; return; }
    const dx = m.x - player.x, dz = m.z - player.z;
    m.progPivot.rotation.y = Math.atan2(-dz, dx);
    m.prog.geometry.setDrawRange(0, 6 * Math.max(1, Math.round(PROG_SEGS * frac)));
    // a tick at every quarter, so it can be heard filling as well as seen
    const q = Math.floor(frac * 4);
    if (q > m.quarter && q < 4) sfx('click');
    m.quarter = q;
  }
  /**
   * Every kind of job its own colour, so a glance across the site says which is which: the hammer's
   * yellow, the mesh blue, the laser pink, the pipe line orange, corners and collars purple, the
   * water tank cyan, your own business green, the van white — and red for now, or it gets worse.
   */
  function markerColour(id) {
    if (/^(block|blowout|rebarUp)$/.test(id)) return 0xff3b30;
    if (/^(form|boardFix)/.test(id)) return 0xffc629;
    if (/^(tie|cut)/.test(id)) return 0x5aa9ff;
    if (/^(laser|batteries)/.test(id)) return 0xff5ca8;
    if (/^(pile|pipe)/.test(id)) return 0xff6b1a;
    if (/^edge/.test(id)) return 0xb48cff;
    if (id === 'wash') return 0x3fd6e6;
    if (/^(loo|phone|spares|behindVan|lunch)$/.test(id)) return 0x6fd08c;
    return 0xf2efe8;
  }
  function addMarker(id, p, label, hold, active, done, opts) {
    const g = new THREE.Group();
    const col = markerColour(id), css = `#${col.toString(16).padStart(6, '0')}`;
    const ringMat = new THREE.MeshBasicMaterial({ color: col });
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
    const beamM = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 2.4, 8), new THREE.MeshBasicMaterial({ color: col, transparent: true, opacity: 0.35 }));
    beamM.position.y = 1.2;
    g.add(beamM);
    const w = (opts && opts.w) || 2.6;
    const sprite = textSprite(label, { w, color: css });
    sprite.position.y = 2.7;
    g.add(sprite);
    g.position.set(p.x, (opts && opts.y) || 0, p.z);
    g.visible = false;
    scene.add(g);
    const m = { id, x: p.x, z: p.z, label, hold, active, done, group: g, ring, beam: beamM, ringMat, prog, progPivot, quarter: 0, sprite, w, col: css, hex: col, tool: opts && opts.tool };
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
    shovel: { name: 'Shovel', the: 'the shovel' },
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
    // the mounting: a rib riveted down the blade, two posts up to the handle
    box(0.2, 0.004, 0.014, 0x9aa0a6, 0.005, 0.002, 0, g);
    [-0.07, -0.02, 0.03, 0.08].forEach((x) => cyl(0.004, 0.004, 0.004, 0x5a5f64, x, 0.005, 0, g, 6));
    box(0.006, 0.05, 0.012, 0x6d7278, -0.04, 0.025, 0, g);
    box(0.006, 0.05, 0.012, 0x6d7278, 0.05, 0.025, 0, g);
    const h = cyl(0.016, 0.018, 0.15, WOOD, 0.005, 0.058, 0, g, 10);
    h.rotation.z = Math.PI / 2;
    // brass ferrules at the ends of the handle
    [-0.072, 0.082].forEach((x) => { const f = cyl(0.019, 0.019, 0.012, 0xc9a24a, x, 0.058, 0, g, 10); f.rotation.z = Math.PI / 2; });
    parent.add(g);
    return g;
  }
  /** A site shovel: yellow fibreglass shaft, black D-grip, a red-painted blade with a worn steel edge. */
  function mkShovel(parent) {
    const g = new THREE.Group();
    cyl(0.02, 0.02, 0.95, 0xf2b705, 0, 0.62, 0, g, 8);
    box(0.14, 0.035, 0.035, 0x1d1f22, 0, 1.12, 0, g);
    [-1, 1].forEach((sd) => box(0.025, 0.11, 0.035, 0x1d1f22, sd * 0.06, 1.07, 0, g));
    cyl(0.03, 0.022, 0.14, 0x5a5f64, 0, 0.12, 0, g, 8);
    // tape round the shaft where the hands go, black and grubby
    cyl(0.023, 0.023, 0.16, 0x1d1f22, 0, 0.82, 0, g, 8);
    cyl(0.023, 0.023, 0.12, 0x1d1f22, 0, 0.42, 0, g, 8);
    const blade = new THREE.Group();
    blade.position.set(0, -0.1, 0.02);
    blade.rotation.x = -0.15;
    box(0.27, 0.3, 0.022, 0xd8392f, 0, 0.02, 0, blade);
    box(0.27, 0.05, 0.024, MATS_STEEL, 0, -0.15, 0, blade);
    [-1, 1].forEach((sd) => box(0.022, 0.3, 0.05, 0xd8392f, sd * 0.135, 0.02, 0.012, blade));
    [-0.04, 0.04].forEach((x) => cyl(0.008, 0.008, 0.01, 0x9ea3a8, x, 0.13, 0.014, blade, 6).rotation.x = Math.PI / 2);
    // concrete dried on the blade, both faces, more of it the dirtier the shovel is
    const crust = new THREE.Group();
    [-1, 1].forEach((sd) => {
      const f = box(0.22, 0.2, 0.006, 0x9a9c99, 0, -0.03, sd * 0.014, blade);
      crust.add(f);
      mesh(new THREE.SphereGeometry(0.035, 7, 5), 0x8f918e, 0.07, -0.08, sd * 0.016, crust).scale.set(1.2, 0.8, 0.35);
      mesh(new THREE.SphereGeometry(0.03, 7, 5), 0x8f918e, -0.08, -0.1, sd * 0.016, crust).scale.set(1, 0.7, 0.35);
    });
    blade.add(crust);
    crust.visible = false;
    g.userData.crust = crust;
    // a load of concrete on it, shown while it carries one
    const load = new THREE.Mesh(new THREE.SphereGeometry(0.11, 10, 8), new THREE.MeshLambertMaterial({ color: 0x7d7f80 }));
    load.scale.set(1.05, 0.9, 0.42);
    load.position.set(0, 0.02, 0.05);
    load.visible = false;
    blade.add(load);
    g.userData.load = load;
    g.add(blade);
    parent.add(g);
    return g;
  }
  /** A claw hammer: hickory handle with a rubber grip, a steel head with a face one side and the claw the other. */
  function mkHammer(parent) {
    const g = new THREE.Group();
    cyl(0.017, 0.02, 0.36, WOOD, 0, 0.18, 0, g, 8);
    cyl(0.022, 0.022, 0.13, 0x1d1f22, 0, 0.07, 0, g, 8);
    // the head: the eye round the handle, the neck to the striking face
    box(0.06, 0.075, 0.06, 0x3a3d41, 0, 0.37, 0, g);
    const face = cyl(0.032, 0.03, 0.07, 0x6d7278, 0.065, 0.37, 0, g, 12);
    face.rotation.z = Math.PI / 2;
    cyl(0.033, 0.033, 0.012, 0xb9bec3, 0.1, 0.37, 0, g, 12).rotation.z = Math.PI / 2;
    // the claw: two tines curling down, split in the middle for the nail
    [-1, 1].forEach((sd) => {
      const t = box(0.1, 0.022, 0.018, 0x3a3d41, -0.075, 0.355, sd * 0.014, g);
      t.rotation.z = 0.45;
    });
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
  let rxLight = null;              // the receiver's window: blue low, red high, green on height
  (function buildViewTools() {
    // The end hose in your hands: only the glove lives here, with the camera. The hose itself is
    // out in the world (hoseFlex, below): off the last pipe, along the ground behind you, up
    // through this glove and out to its mouth over wherever the concrete is going.
    const hose = new THREE.Group();
    const grip = new THREE.Group();
    grip.position.set(-0.07, -0.1, -0.3);
    hose.add(grip);
    [-0.03, -0.01, 0.01, 0.03].forEach((y, k) => {
      const f = capsule(0.017, 0.07, 0xc79a5c, -0.03, y, 0.035 - Math.abs(k - 1.5) * 0.004, grip);
      f.rotation.z = Math.PI / 2 + 0.25;
    });
    capsule(0.02, 0.05, 0xb88a4e, 0.05, 0.035, 0.02, grip).rotation.x = 0.6;   // the thumb
    box(0.08, 0.1, 0.06, 0xc79a5c, 0.02, 0, -0.045, grip);                     // the back of the glove
    box(0.085, 0.03, 0.065, 0x8a6a3a, 0.02, -0.06, -0.04, grip);               // the cuff
    hose.userData.grip = grip;
    viewTools.hose = hose;
    // washing up: a green garden hose with a spray gun, not the pump's
    const wash = new THREE.Group();
    const washPath = new THREE.CatmullRomCurve3([
      new THREE.Vector3(0.25, -0.7, 0.25), new THREE.Vector3(0.1, -0.3, 0), new THREE.Vector3(0, -0.12, -0.3),
    ]);
    wash.add(new THREE.Mesh(new THREE.TubeGeometry(washPath, 24, 0.016, 8, false), new THREE.MeshLambertMaterial({ color: 0x3d8b3d })));
    const gun = new THREE.Group();
    gun.position.copy(washPath.getPoint(1));
    wash.add(gun);
    box(0.05, 0.12, 0.05, 0xf2b705, 0, 0.03, 0, gun).rotation.x = 0.3;
    cyl(0.014, 0.018, 0.16, 0x44484d, 0, 0.07, -0.08, gun, 10).rotation.x = Math.PI / 2 - 0.2;
    box(0.02, 0.05, 0.03, 0x1d1f22, 0, 0.0, -0.035, gun);
    wash.visible = false;
    hands.add(wash);
    viewTools.washHose = wash;
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
    const shovel = new THREE.Group();
    // the stroke turns about the blade, not the hands: it's the blade that stabs, scoops and throws
    const pivot = new THREE.Group();
    shovel.add(pivot);
    const shb = mkShovel(pivot);
    shovel.userData.load = shb.userData.load;
    shovel.userData.pivot = pivot;
    // the grip in your hands, the shaft out in front, the blade down where you can see it
    // held low and across you: the shaft from your right hand out to the left, the blade out in front
    // where you can see into it
    {
      const d = new THREE.Vector3(0.65, 0.35, 0.55).normalize();          // blade -> grip
      const bladeAt = new THREE.Vector3(-0.12, -0.42, -0.85);             // from the eye
      const toEye = bladeAt.clone().negate().normalize();
      const n = toEye.clone().addScaledVector(d, -toEye.dot(d)).normalize();
      const x = new THREE.Vector3().crossVectors(d, n);
      shb.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, d, n));
      shb.scale.setScalar(0.75);
      // the tool group sits at the hands (0.32, -0.34, -0.62 in landscape); the pivot at the blade,
      // whose middle is 0.1 below the shovel's origin
      pivot.position.copy(bladeAt).sub(new THREE.Vector3(0.32, -0.34, -0.62));
      pivot.userData.home = pivot.position.clone();
      shb.position.set(0, 0, 0).addScaledVector(d, 0.1 * 0.75);
    }
    viewTools.shovel = shovel;
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
    // the levelling staff, upright in the hand, with the receiver clipped on at slab height
    const st = new THREE.Group();
    for (let k = 0; k < 8; k++) box(0.04, 0.25, 0.02, k % 2 ? 0xf2f0ea : 0xf2b705, 0, -0.5 + k * 0.25, 0, st);
    for (let k = 0; k < 16; k++) box(0.012, 0.006, 0.021, 0x1d1f22, -0.012, -0.55 + k * 0.125, 0.001, st);
    const rx = new THREE.Group();
    box(0.09, 0.14, 0.04, 0xff6b1a, 0, 0, 0.03, rx);
    box(0.06, 0.03, 0.005, 0x2a2d31, 0, 0.03, 0.052, rx);
    rxLight = box(0.05, 0.018, 0.006, 0x3a3d41, 0, -0.03, 0.052, rx);
    rx.position.y = 0.35;
    st.add(rx);
    st.position.set(-0.12, 0.05, -0.28);
    st.rotation.set(0.05, 0, -0.08);
    viewTools.staff = st;
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
    add('shovel', (g) => { const sh = mkShovel(g); sh.rotation.z = -Math.PI / 2; sh.position.set(-0.55, 0.03, 0); });
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
    // the engine's bits: the fuel tank and its cap, the air filter, the exhaust and its heat
    // shield, the pull-start with its handle hanging off the side
    box(0.2 * k, 0.09 * k, 0.16 * k, 0xd8392f, -0.04 * k, 0.2 + 0.4 * k, 0.02, g);
    cyl(0.025 * k, 0.025 * k, 0.03, 0x1d1f22, 0.02 * k, 0.2 + 0.46 * k, 0.04, g, 8);
    cyl(0.06 * k, 0.06 * k, 0.08 * k, 0x1d1f22, 0.15 * k, 0.2 + 0.32 * k, -0.1 * k, g, 12);
    const muffler = cyl(0.045 * k, 0.045 * k, 0.16 * k, 0xb9bec3, -0.19 * k, 0.2 + 0.2 * k, -0.08 * k, g, 10);
    muffler.rotation.x = Math.PI / 2;
    box(0.02, 0.1 * k, 0.18 * k, 0x6d7278, -0.24 * k, 0.2 + 0.2 * k, -0.08 * k, g);
    const pull = cyl(0.07 * k, 0.07 * k, 0.04, 0x2a2d31, 0.19 * k, 0.2 + 0.18 * k, 0.1 * k, g, 12);
    pull.rotation.z = Math.PI / 2;
    box(0.02, 0.06, 0.015, 0x111111, 0.22 * k, 0.2 + 0.1 * k, 0.1 * k, g);
    box(0.13 * k, 0.06 * k, 0.005, 0xf2f0ea, 0, 0.2 + 0.18 * k, 0.141 * k, g);
    // the handle runs back to waist height, where the operator's hands are
    const grip = 1.75;
    const handle = cyl(0.02, 0.02, Math.hypot(grip - 0.1, 0.5), DARK, 0, 0.7, (grip + 0.1) / 2, g);
    handle.rotation.x = Math.atan2(grip - 0.1, 0.5);
    box(0.56, 0.03, 0.03, DARK, 0, 0.95, grip, g);
    box(0.08, 0.04, 0.04, 0x111111, 0.24, 0.95, grip, g);
    box(0.08, 0.04, 0.04, 0x111111, -0.24, 0.95, grip, g);
    // the throttle lever at the right hand, its cable down the handle, and the blade-pitch crank
    box(0.03, 0.015, 0.09, 0xd8392f, 0.2, 0.975, grip - 0.06, g);
    const cable = cyl(0.006, 0.006, Math.hypot(grip - 0.1, 0.5), 0x1d1f22, 0.03, 0.72, (grip + 0.1) / 2, g, 5);
    cable.rotation.x = Math.atan2(grip - 0.1, 0.5);
    cyl(0.012, 0.012, 0.16, 0x6d7278, 0, 1.03, grip - 0.12, g, 6);
    box(0.12, 0.018, 0.018, 0x1d1f22, 0, 1.11, grip - 0.12, g);
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
    // vents in the engine cover, the fuel cap, a seat cushion, the rubber grips on the sticks
    for (let n = 0; n < 5; n++) box(0.6, 0.012, 0.02, 0x1d1f22, 0, 0.62 + n * 0.05, 0.001, g);
    cyl(0.04, 0.04, 0.03, 0x1d1f22, -0.3, 0.78, 0.15, g, 10);
    box(0.46, 0.05, 0.4, 0x2a2d31, 0, 0.9, 0.3, g);
    [-0.42, 0.42].forEach((x) => cyl(0.02, 0.02, 0.1, 0x111111, x, 0.84, -0.12, g, 8));
    // an orange beacon on a stalk, lit and turning while the engine runs
    cyl(0.012, 0.012, 0.35, 0x2a2d31, 0.4, 1.2, 0.5, g, 6);
    const beacon = mesh(new THREE.CylinderGeometry(0.045, 0.05, 0.08, 12), new THREE.MeshLambertMaterial({ color: 0xff8a1a, emissive: 0x000000 }), 0.4, 1.41, 0.5, g);
    scene.add(g);
    return { group: g, rotors: [left, right], R, grip: 0, twin: true, beacon };
  }
  const machines = { trowelSmall: mkTrowel(0.3), trowelBig: mkTrowel(0.46), rideOn: mkRideOn() };
  Object.values(machines).forEach((m) => { m.group.visible = false; m.spin = 0; });

  // a magnesium float on a pole, for levelling the pour and smoothing out prints
  const floatTool = new THREE.Group();
  box(0.8, 0.02, 0.2, 0xaeb4b9, 0, 0.012, 0, floatTool);
  [-0.4, 0.4].forEach((x) => cyl(0.1, 0.1, 0.02, 0xaeb4b9, x, 0.012, 0, floatTool, 16));
  [-0.07, 0.07].forEach((z) => box(0.86, 0.02, 0.012, 0x8e959b, 0, 0.03, z, floatTool));
  box(0.1, 0.05, 0.06, 0x5a5f64, 0, 0.05, 0, floatTool);
  const fpv = cyl(0.018, 0.018, 0.1, 0x2a2d31, 0, 0.08, 0, floatTool, 8);
  fpv.rotation.x = Math.PI / 2;
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
  // Concrete out of the hose: out of its mouth and down, lumpy, swelling with every stroke of the
  // pump — the surge travels down it — and slapping onto the slab where it lands.
  let streamOn = false;
  const Y_UP = new THREE.Vector3(0, 1, 0);
  const streamMat = new THREE.MeshPhongMaterial({ color: 0x5f6264, specular: 0x6a6a6a, shininess: 70, flatShading: true });
  /**
   * A tube whose shape is set every frame — the hose in your hands, the concrete falling out of it.
   * Its buffers are made once and rewritten in place, so bending it costs no garbage.
   */
  function flexTube(nSeg, nRad, mat) {
    const cols = nRad + 1, rows = nSeg + 1, idx = [];
    for (let i = 0; i < nSeg; i++) for (let j = 0; j < nRad; j++) { const a = i * cols + j, b = a + cols; idx.push(a, a + 1, b, b, a + 1, b + 1); }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(rows * cols * 3), 3));
    g.setAttribute('normal', new THREE.BufferAttribute(new Float32Array(rows * cols * 3), 3));
    g.setIndex(idx);
    const m = new THREE.Mesh(g, mat);
    m.frustumCulled = false;
    m.visible = false;
    m.userData = { nSeg, nRad, pts: Array.from({ length: rows }, () => new THREE.Vector3()), rad: new Float32Array(rows).fill(1) };
    scene.add(m);
    return m;
  }
  const ftT = new THREE.Vector3(), ftN = new THREE.Vector3(), ftB = new THREE.Vector3();
  /** Lays a flexTube along its points, each ring [radius] times that ring's own size. */
  function bendTube(m, radius) {
    const { nSeg, nRad, pts, rad } = m.userData, cols = nRad + 1;
    const pos = m.geometry.attributes.position.array, nor = m.geometry.attributes.normal.array;
    for (let i = 0; i <= nSeg; i++) {
      ftT.subVectors(pts[Math.min(nSeg, i + 1)], pts[Math.max(0, i - 1)]);
      if (ftT.lengthSq() < 1e-10) ftT.set(0, 1, 0);
      ftT.normalize();
      if (i === 0) { if (Math.abs(ftT.y) > 0.9) ftN.set(1, 0, 0); else ftN.set(0, 1, 0); }
      // each ring's frame carried on from the last one, so the tube doesn't twist
      ftN.addScaledVector(ftT, -ftN.dot(ftT)).normalize();
      ftB.crossVectors(ftT, ftN);
      const r = radius * rad[i], p = pts[i];
      for (let j = 0; j <= nRad; j++) {
        const th = (j / nRad) * Math.PI * 2, c = Math.cos(th), sn = Math.sin(th);
        const nx = c * ftN.x + sn * ftB.x, ny = c * ftN.y + sn * ftB.y, nz = c * ftN.z + sn * ftB.z;
        const k = (i * cols + j) * 3;
        pos[k] = p.x + nx * r; pos[k + 1] = p.y + ny * r; pos[k + 2] = p.z + nz * r;
        nor[k] = nx; nor[k + 1] = ny; nor[k + 2] = nz;
      }
    }
    m.geometry.attributes.position.needsUpdate = true;
    m.geometry.attributes.normal.needsUpdate = true;
  }
  const streamFlex = flexTube(20, 9, streamMat);
  const sA = new THREE.Vector3();
  function hideStream() { streamFlex.visible = false; }
  /**
   * Concrete out of the hose's mouth [a] onto [b]: it leaves the mouth moving the way the mouth
   * points and falls — straight down when the mouth is over the spot, bending over when it's
   * thrown a little way out — lumpy, swelling with every stroke of the pump.
   */
  function drawStream(a, b, dt) {
    const t = performance.now() / 1000;
    const { nSeg, pts, rad } = streamFlex.userData;
    const out = clamp(hyp(a.x, a.z, b.x, b.z) / 2.5, 0, 1);
    const stroke = gs.mixState === 'stiff' ? 6 : 7.5;
    for (let i = 0; i <= nSeg; i++) {
      const u = i / nSeg;
      // thrown out it arcs over a little before it drops; poured straight down it just falls
      pts[i].set(lerp(a.x, b.x, u), lerp(a.y, b.y, lerp(u, u * u, out)) + out * 0.28 * 4 * u * (1 - u), lerp(a.z, b.z, u));
      if (!CALM && i > 0 && i < nSeg) { pts[i].x += Math.sin(t * 17 + i) * 0.006; pts[i].z += Math.cos(t * 13 + i * 1.3) * 0.006; }
      // a pump stroke travels down it as a bulge, and it's never a smooth rod: stones and lumps
      const surge = CALM ? 0.4 : Math.max(0, Math.sin(t * stroke - i * 0.55));
      // lumps of it, not a rod: stones and clots travel down it, and it breaks up as it falls
      const lumps = CALM ? 0 : 0.22 * Math.sin(t * 23 + i * 1.9) + 0.14 * Math.sin(t * 41 + i * 3.1) + 0.1 * Math.sin(t * 67 + i * 5.3);
      rad[i] = Math.max(0.35, (1 + 0.45 * surge * surge + lumps) * (1 - 0.3 * u));
    }
    rad[0] = 0.9;
    bendTube(streamFlex, gs.mixState === 'soup' ? 0.043 : 0.05);
    streamFlex.visible = true;
    // lumps falling off it on the way down, more the further it's thrown
    const drops = CALM ? 0 : Math.round(1 + out * 2 + Math.random());
    for (let k = 0; k < drops; k++) { sA.copy(pts[irnd(2, nSeg - 2)]); emit(sA.x + rnd(-0.04, 0.04), sA.y, sA.z + rnd(-0.04, 0.04), rnd(-0.25, 0.25), rnd(-0.6, 0.1), rnd(-0.25, 0.25), 0.5, pick([0x6c6e70, 0x5e6062, 0x7d7f80]), rnd(0.015, 0.035)); }
    // where it lands: it slaps and throws some back up, more on the surge
    const peak = Math.max(0, Math.sin(t * stroke - nSeg * 0.55));
    const n = Math.round((2 + peak * 5) * Math.min(1, dt * 60));
    for (let k = 0; k < n; k++) {
      const ang = rnd(0, Math.PI * 2), sp = rnd(0.4, 1.4);
      emit(b.x, b.y + 0.02, b.z, Math.cos(ang) * sp, rnd(0.6, 1.8), Math.sin(ang) * sp, rnd(0.3, 0.6), pick([0x7d7f80, 0x8c8e90, 0x6d6f71]), rnd(0.025, 0.05));
    }
  }

  // The end hose in your hands, as the world sees it: from the last pipe (or down off the boom),
  // along the ground behind you, up through your glove and out to its mouth. The mouth goes over
  // the spot you're pouring — as far out as the hose and your arms go — and hangs in front of you
  // when you're not. It swings there rather than jumping, and kicks with every stroke.
  const hoseFlex = flexTube(56, 10, new THREE.MeshPhongMaterial({ color: 0x1c1e21, specular: 0x3a3a3a, shininess: 22 }));
  const hoseMouth = new THREE.Group();
  mesh(new THREE.CylinderGeometry(0.062, 0.06, 0.05, 16, 1, true), new THREE.MeshPhongMaterial({ color: 0x2a2d31, side: THREE.DoubleSide, shininess: 10 }), 0, -0.02, 0, hoseMouth); // the worn lip
  const mouthIn = cyl(0.05, 0.05, 0.004, 0x151618, 0, -0.03, 0, hoseMouth, 14);   // dark inside, or grey when it's coming
  mesh(new THREE.CylinderGeometry(0.061, 0.061, 0.03, 14, 1, true), 0xb9bec3, 0, -0.3, 0, hoseMouth);  // a steel clamp where it was mended
  const mouthDark = mouthIn.material, mouthWet = lam(0x6c6e70);
  hoseMouth.visible = false;
  scene.add(hoseMouth);
  const hoseCurve = new THREE.CatmullRomCurve3(Array.from({ length: 8 }, () => new THREE.Vector3()), false, 'centripetal');
  const hoseM = new THREE.Vector3(NaN, 0, 0), hoseD = new THREE.Vector3(0, -1, 0);
  const hH = new THREE.Vector3(), hW = new THREE.Vector3(), hT = new THREE.Vector3(), hQ = new THREE.Quaternion();
  /** The hose in hand this frame; [land] is where the concrete is going, or null. Returns the mouth. */
  function updateHose(land, dt) {
    const grip = viewTools.hose.userData.grip;
    grip.updateMatrixWorld(true);
    grip.getWorldPosition(hH);
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    // Where the mouth wants to be: the last half metre of a heavy rubber hose, held out ahead of
    // your hands and aimed — not two metres of it waving in the air to reach the spot. The concrete
    // goes the rest of the way itself: straight down when the spot is under you, thrown out a
    // little when it's further off.
    let dh = 0, dx = 0, dz = 0;
    if (land) {
      dx = land.x - hH.x; dz = land.z - hH.z; dh = Math.hypot(dx, dz) || 0.001;
      const out = Math.min(0.55, dh * 0.6);
      hW.set(hH.x + (dx / dh) * out, Math.max(land.y + 0.3, hH.y - 0.3), hH.z + (dz / dh) * out);
    } else hW.set(hH.x + fx * 0.35, hH.y - 0.45, hH.z + fz * 0.35);
    if (!Number.isFinite(hoseM.x) || hoseM.distanceTo(hW) > 3) hoseM.copy(hW);
    else hoseM.lerp(hW, 1 - Math.exp(-dt * 9));
    const kick = streamOn && !CALM ? Math.sin(toolT * 50) * 0.008 + Math.max(0, Math.sin(toolT * 7.5)) * 0.03 : 0;
    // which way the mouth points: along the way the concrete leaves it — out and down for a spot
    // further off, straight down for one under it — or hanging down
    if (land) {
      const k = clamp(dh / 2.5, 0, 1);
      hoseD.set((dx / dh) * (0.35 + k), -1.1 + k * 1.3, (dz / dh) * (0.35 + k)).normalize();
    } else hoseD.set(fx * 0.35, -1, fz * 0.35).normalize();
    // back along the ground towards the pipe it comes from, or up to the boom's tip
    const last = pipeEnd(), src = day.boom ? boomTip : hT.set(last.x, 0.15, last.z);
    const P = hoseCurve.points;
    const bx = src.x - player.x, bz = src.z - player.z, bl = Math.hypot(bx, bz) || 1;
    if (day.boom) {
      P[0].copy(src); P[1].lerpVectors(src, hH, 0.35).y -= 0.4; P[2].lerpVectors(src, hH, 0.7).y -= 0.3;
    } else {
      // down to the ground just behind you on the right, the hand side, so it comes up from behind
      // your hands rather than across the view
      const rx = Math.cos(player.yaw), rz = -Math.sin(player.yaw);
      const gx0 = player.x + (bx / bl) * Math.min(1.1, bl) - fx * 0.5 + rx * 0.35, gz0 = player.z + (bz / bl) * Math.min(1.1, bl) - fz * 0.5 + rz * 0.35;
      P[0].copy(src);
      P[1].set((src.x + gx0) / 2, 0, (src.z + gz0) / 2); P[1].y = groundY(P[1].x, P[1].z) + 0.06;
      P[2].set(gx0, groundY(gx0, gz0) + 0.06, gz0);
    }
    P[3].set(hH.x - fx * 0.3, hH.y - 0.55, hH.z - fz * 0.3);
    P[4].copy(hH);
    P[5].lerpVectors(hH, hoseM, 0.5); P[5].y += 0.03 + kick * 0.5;
    P[6].copy(hoseM).addScaledVector(hoseD, -0.14); P[6].y += kick;
    P[7].copy(hoseM); P[7].y += kick;
    const { nSeg, pts } = hoseFlex.userData;
    for (let i = 0; i <= nSeg; i++) hoseCurve.getPoint(i / nSeg, pts[i]);
    bendTube(hoseFlex, 0.055);
    hoseFlex.visible = true;
    hoseMouth.position.copy(P[7]);
    hoseMouth.quaternion.setFromUnitVectors(Y_UP, hoseD);
    hoseMouth.visible = true;
    mouthIn.material = land ? mouthWet : mouthDark;
    // the glove turned to hold the hose the way it runs through it
    hoseCurve.getTangent(4 / 7, hT);
    grip.parent.getWorldQuaternion(hQ).invert();
    grip.quaternion.setFromUnitVectors(Y_UP, hT.applyQuaternion(hQ).normalize());
    return P[7];
  }
  function hideHose() { hoseFlex.visible = false; hoseMouth.visible = false; }
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
  const storedNum = (key, def) => { const v = Number(store(key)); return store(key) !== null && store(key) !== undefined && store(key) !== '' && Number.isFinite(v) ? v : def; };
  let soundVol = storedNum('pourday.volSound', 1), musicVol = storedNum('pourday.volMusic', 1);
  const loops = {};
  function audioStart() {
    if (ac) { if (ac.state === 'suspended' && !document.hidden) ac.resume().catch(() => {}); return; }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    try { ac = new AC(); } catch (e) { ac = null; return; }
    master = ac.createGain();
    master.gain.value = soundOn ? 0.9 * soundVol : 0;
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
    if (master) master.gain.setTargetAtTime(on ? 0.9 * soundVol : 0, ac.currentTime, 0.05);
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
      case 'hammer': {
        const k = rnd(0.88, 1.12);
        tone(t, 'sine', 170 * k, 70 * k, 0.4, 0.002, 0.14, dest);
        burst(t, 'bandpass', 1600 * k, 1.2, 0.35, 0.001, 0.07, dest);
        tone(t, 'triangle', 1900 * k, 1850 * k, 0.05, 0.001, 0.12, dest);
        // the stake answering, a hollow wooden knock
        tone(t + 0.01, 'sine', 420 * k, 380 * k, 0.12, 0.002, 0.09, dest);
        break;
      }
      case 'beep': tone(t, 'sine', 2900, 2900, 0.12, 0.005, 0.07, dest); tone(t + 0.13, 'sine', 2900, 2900, 0.12, 0.005, 0.07, dest); break;
      case 'honk':
        [0, 0.45].forEach((d) => { tone(t + d, 'square', 390, 385, 0.1, 0.02, 0.32, dest); tone(t + d, 'square', 494, 490, 0.08, 0.02, 0.32, dest); });
        break;
      case 'reverse': for (let k = 0; k < 4; k++) tone(t + k * 0.5, 'square', 1050, 1050, 0.05, 0.005, 0.26, dest); break;
      // the air brake: a clunk, then the hiss
      case 'brake': tone(t, 'sine', 180, 90, 0.1, 0.003, 0.1, dest); burst(t + 0.05, 'bandpass', 3200, 0.9, 0.5, 0.005, 0.55, dest); burst(t + 0.05, 'highpass', 5000, 0.5, 0.22, 0.005, 0.35, dest); break;
      case 'beep1': tone(t, 'square', 1100, 1100, 0.05, 0.005, 0.32, dest); break;
      case 'stone': burst(t, 'highpass', 2600, 1, 0.08, 0.001, 0.03, dest); tone(t, 'triangle', rnd(1500, 2600), rnd(1200, 2000), 0.03, 0.001, 0.05, dest); break;
      case 'bark': [0, 0.22].forEach((d) => { tone(t + d, 'sawtooth', 560, 280, 0.2, 0.01, 0.1, dest); burst(t + d, 'bandpass', 900, 2, 0.25, 0.005, 0.1, dest); }); break;
      case 'voice': voice(t, rnd(110, 210), irnd(4, 8), dest); break;
      case 'thud': tone(t, 'sine', 95, 38, 0.6, 0.005, 0.35, dest); burst(t, 'lowpass', 400, 0.7, 0.4, 0.005, 0.25, dest); break;
      case 'retch': {
        // two heaves and what follows them
        [0, 0.55].forEach((d, k) => {
          tone(t + d, 'sawtooth', 135 - k * 15, 70, 0.22, 0.03, 0.4, dest);
          const s2 = noiseSrc(); const f = filt('bandpass', 500, 3); const g2 = ac.createGain();
          f.frequency.setValueAtTime(380, t + d); f.frequency.linearRampToValueAtTime(900, t + d + 0.35);
          env(g2, t + d, 0.04, 0.28, 0.4); chain(s2, f, g2, dest); s2.start(t + d); s2.stop(t + d + 0.5);
        });
        burst(t + 1.05, 'lowpass', 700, 0.8, 0.5, 0.01, 0.6, dest);
        burst(t + 1.1, 'bandpass', 420, 1.5, 0.3, 0.02, 0.5, dest);
        break;
      }
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
      case 'pick_steel': { const f = rnd(1900, 2600); tone(t, 'triangle', f, f * 0.98, 0.1, 0.001, 0.25, dest); tone(t, 'sine', f * 2.7, f * 2.6, 0.04, 0.001, 0.18, dest); burst(t, 'highpass', 3500, 0.7, 0.12, 0.001, 0.04, dest); break; }
      case 'pick_alu': tone(t, 'sine', 780, 760, 0.12, 0.002, 0.5, dest); tone(t, 'sine', 1960, 1930, 0.05, 0.002, 0.35, dest); burst(t, 'bandpass', 1800, 1.5, 0.1, 0.002, 0.05, dest); break;
      case 'pick_rubber': burst(t, 'lowpass', 500, 0.8, 0.25, 0.01, 0.18, dest); tone(t, 'sine', 120, 80, 0.15, 0.005, 0.15, dest); break;
      case 'pick_shovel': burst(t, 'bandpass', 1400, 1.2, 0.2, 0.004, 0.2, dest); tone(t + 0.02, 'triangle', 620, 600, 0.07, 0.002, 0.3, dest); break;
      case 'stab': burst(t, 'bandpass', 900, 1.1, 0.28, 0.005, 0.18, dest); burst(t + 0.05, 'lowpass', 420, 1, 0.3, 0.02, 0.25, dest); break;
      case 'slop': burst(t, 'lowpass', 600, 1.2, 0.35, 0.01, 0.3, dest); tone(t, 'sine', 180, 60, 0.18, 0.01, 0.25, dest); break;
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
      case 'grade': tone(t, 'square', 2900, 2900, 0.04, 0.004, 0.55, dest); tone(t, 'sine', 1450, 1450, 0.03, 0.004, 0.55, dest); break;
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
      case 'snore': {
        // in through the nose, out through a rattle
        const s2 = noiseSrc(), f = filt('bandpass', 500, 2), g2 = ac.createGain();
        f.frequency.setValueAtTime(350, t); f.frequency.linearRampToValueAtTime(900, t + 1.2);
        env(g2, t, 0.5, 0.1, 0.8); chain(s2, f, g2, dest); s2.start(t); s2.stop(t + 1.5);
        const o = ac.createOscillator(), am = ac.createGain(), lf = ac.createOscillator(), ld = ac.createGain(), g3 = ac.createGain();
        o.type = 'sawtooth'; o.frequency.value = 62; am.gain.value = 0;
        lf.type = 'square'; lf.frequency.value = 28; ld.gain.value = 0.5; lf.connect(ld); ld.connect(am.gain);
        env(g3, t + 1.4, 0.15, 0.14, 1.0);
        chain(o, filt('lowpass', 400, 1), am, g3, dest);
        o.start(t + 1.4); lf.start(t + 1.4); o.stop(t + 2.7); lf.stop(t + 2.7);
        break;
      }
      case 'gurgle': {
        // a stomach with opinions: a low growl that wobbles as it goes down
        const o = ac.createOscillator(), g2 = ac.createGain(), v = ac.createOscillator(), vd = ac.createGain();
        o.type = 'sawtooth'; o.frequency.setValueAtTime(95, t); o.frequency.exponentialRampToValueAtTime(52, t + 1.1);
        v.frequency.value = 11; vd.gain.value = 18; v.connect(vd); vd.connect(o.frequency);
        env(g2, t, 0.08, 0.16, 1.0);
        chain(o, filt('lowpass', 260, 4), g2, dest);
        o.start(t); v.start(t); o.stop(t + 1.3); v.stop(t + 1.3);
        burst(t + 0.2, 'lowpass', 300, 2, 0.12, 0.05, 0.6, dest);
        break;
      }
      case 'rattle':
        // the knob pulled against the lock, and the door thumping in its frame
        for (let k = 0; k < 4; k++) { tone(t + k * 0.09, 'triangle', rnd(900, 1300), rnd(800, 1100), 0.08, 0.001, 0.06, dest); burst(t + k * 0.09, 'bandpass', 2200, 2, 0.12, 0.001, 0.04, dest); }
        tone(t + 0.4, 'sine', 120, 70, 0.25, 0.003, 0.15, dest);
        break;
      case 'flush': burst(t, 'bandpass', 900, 0.6, 0.3, 0.1, 1.4, dest); burst(t + 0.2, 'lowpass', 500, 0.8, 0.25, 0.2, 1.2, dest); tone(t + 0.1, 'sine', 300, 120, 0.05, 0.1, 1.0, dest); break;
      case 'trickle': for (let k = 0; k < 10; k++) burst(t + k * 0.12, 'bandpass', rnd(2500, 3800), 3, 0.06, 0.01, 0.1, dest); break;
      case 'squelch': burst(t, 'lowpass', 700, 3, 0.12, 0.01, 0.12, dest); tone(t, 'sine', 240, 90, 0.05, 0.005, 0.1, dest); break;
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
    // The line pump: a big diesel working against the hydraulics, and a pump whine that follows
    // the load. The strokes themselves — the swing tube slamming over, the push down the line —
    // come one at a time from pumpStroke, while the concrete's going somewhere.
    make('pump', (g) => {
      loops._pumpEng = diesel(1500, g, 420, 0.55);
      const w = ac.createOscillator(); w.type = 'triangle'; w.frequency.value = 340;
      const wg = ac.createGain(); wg.gain.value = 0;
      lfo(0.6, 6, w.frequency);
      chain(w, filt('bandpass', 700, 3), wg, g); w.start();
      loops._pumpWhine = wg;
    });
    // The mixer: its diesel ticking over, or revved to turn the drum fast while it empties; the drum
    // itself — tonnes of wet stone lifted by the blades and dropped, over and over — and the slide
    // of it down the chute into the pump's hopper.
    make('mixer', (g) => {
      loops._mixEng = diesel(720, g, 380, 0.5);
      const drops = ac.createOscillator(); drops.frequency.value = 0.12; drops.start();
      const n = noiseSrc(true), churn = ac.createGain(); churn.gain.value = 0.25;
      const d1 = ac.createGain(); d1.gain.value = 0.22; drops.connect(d1); d1.connect(churn.gain);
      chain(n, filt('bandpass', 620, 0.8), churn, g); n.start();
      const n2 = noiseSrc(true), slosh = ac.createGain(); slosh.gain.value = 0.3;
      const d2 = ac.createGain(); d2.gain.value = 0.25; drops.connect(d2); d2.connect(slosh.gain);
      chain(n2, filt('lowpass', 240, 0.9), slosh, g); n2.start();
      loops._drum = drops;
      const wh = ac.createOscillator(); wh.type = 'triangle'; wh.frequency.value = 140;
      const whg = ac.createGain(); whg.gain.value = 0.025; chain(wh, whg, g); wh.start();
      loops._drumWhine = wh;
      const c = noiseSrc(true), cw = ac.createGain(), cg = ac.createGain(); cw.gain.value = 0.7; cg.gain.value = 0;
      lfo(2.7, 0.3, cw.gain);
      chain(c, filt('lowpass', 800, 0.7), cw, cg, g); c.start();
      loops._chute = cg;
    });
    // the power trowel: a small petrol engine and the disc hissing over the paste
    make('trowel', (g) => {
      const o = ac.createOscillator(); o.type = 'sawtooth'; o.frequency.value = 62;
      const o2 = ac.createOscillator(); o2.type = 'square'; o2.frequency.value = 31;
      const lp = filt('lowpass', 900, 1.5);
      const og = ac.createGain(); og.gain.value = 0.45;
      lfo(9, 6, o.frequency);
      // the firing: each stroke a puff, so the note putters instead of humming
      const fire = ac.createGain(); fire.gain.value = 0.6;
      const fl = ac.createOscillator(); fl.type = 'square'; fl.frequency.value = 26;
      const fd = ac.createGain(); fd.gain.value = 0.4; fl.connect(fd); fd.connect(fire.gain); fl.start();
      chain(o, lp, fire, og, g); o.start();
      const o2g = ac.createGain(); o2g.gain.value = 0.2; chain(o2, filt('lowpass', 300, 1), o2g, g); o2.start();
      loops._trowelOsc = o; loops._trowelOsc2 = o2; loops._trowelFire = fl;
      // the engine rattling on its mounts
      const rn = noiseSrc(true); const rg = ac.createGain(); rg.gain.value = 0.12;
      lfo(13, 0.1, rg.gain); chain(rn, filt('bandpass', 1700, 2), rg, g); rn.start();
      const n = noiseSrc(true); const bp = filt('bandpass', 2200, 0.9);
      const ng = ac.createGain(); ng.gain.value = 0;
      chain(n, bp, ng, g); n.start();
      loops._trowelHiss = ng;
      const cn = noiseSrc(true); const cg = ac.createGain(); cg.gain.value = 0;
      const cl = ac.createGain(); cl.gain.value = 0.5; lfo(22, 0.5, cl.gain);
      chain(cn, filt('highpass', 3400, 0.8), cl, cg, g); cn.start();
      loops._trowelClatter = cg;
    });
    // concrete out of the hose, and water out of the other hose
    make('pour', (g) => {
      const n = noiseSrc(true); const lp = filt('lowpass', 480, 1.2);
      const gl = ac.createGain(); gl.gain.value = 0.7;
      lfo(7.5, 0.35, gl.gain);
      chain(n, lp, gl, g); n.start();
      const o = ac.createOscillator(); o.frequency.value = 55;
      const th = ac.createGain(); th.gain.value = 0.2; lfo(1.2, 0.2, th.gain);
      chain(o, th, g); o.start();
    });
    make('water', (g) => { const n = noiseSrc(true); chain(n, filt('bandpass', 2600, 0.5), g); n.start(); });
    // a float or a hand trowel scraping
    make('scrape', (g) => {
      const n = noiseSrc(true); const bp = filt('bandpass', 1100, 1.4);
      const sg = ac.createGain(); sg.gain.value = 0.6;
      lfo(2.2, 0.4, sg.gain);
      chain(n, bp, sg, g); n.start();
      // the grit under the blade: a thinner, rougher layer that comes and goes faster
      const n2 = noiseSrc(true); const g2 = ac.createGain(); g2.gain.value = 0.25;
      lfo(5.3, 0.22, g2.gain);
      chain(n2, filt('bandpass', 3100, 2.2), g2, g); n2.start();
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
    // whatever's driving in: the pump, the mixer
    make('engine', (g) => { loops._driveEng = diesel(900, g, 380, 0.6); });
  }
  // A diesel, as one cycle of a six-cylinder four-stroke: two turns of the crank and six firings.
  // Loud on the firing harmonics, a little of everything else for cylinders that aren't quite equal
  // — which is what makes it chug instead of hum.
  let engWave = null;
  function engineWave() {
    if (engWave) return engWave;
    const n = 48, re = new Float32Array(n), im = new Float32Array(n);
    for (let k = 1; k < n; k++) {
      const a = (k % 6 === 0 ? 1 : k % 3 === 0 ? 0.4 : 0.16) / Math.pow(k, 0.5);
      const ph = Math.random() * Math.PI * 2;
      re[k] = a * Math.cos(ph); im[k] = a * Math.sin(ph);
    }
    engWave = ac.createPeriodicWave(re, im);
    return engWave;
  }
  /** A diesel into [dest] at [rpm]: the block's note, the exhaust chuffing with it, the tin rattling. Its oscillator sets the revs. */
  function diesel(rpm, dest, cutoff, level) {
    const o = ac.createOscillator();
    o.setPeriodicWave(engineWave());
    o.frequency.value = rpm / 120;
    const og = ac.createGain(); og.gain.value = level || 0.6;
    chain(o, filt('lowpass', cutoff || 420, 0.8), og, dest); o.start();
    const n = noiseSrc(true), ng = ac.createGain(), am = ac.createGain();
    ng.gain.value = 0; am.gain.value = 0.28; o.connect(am); am.connect(ng.gain);
    chain(n, filt('bandpass', 480, 0.7), ng, dest); n.start();
    const r = noiseSrc(true), rg = ac.createGain(), am2 = ac.createGain();
    rg.gain.value = 0; am2.gain.value = 0.05; o.connect(am2); am2.connect(rg.gain);
    chain(r, filt('bandpass', 2400, 1.2), rg, dest); r.start();
    return o;
  }
  /** One stroke of the line pump: the swing tube slamming across, then the next piston's push down the line. */
  let strokeAt = 0, pumpRpm = 1500, beepAt = 0;
  function pumpStroke(x, z, level) {
    const t = ac.currentTime + 0.01;
    const dest = ac.createGain();
    dest.gain.value = level;
    dest.connect(out(x, z));
    burst(t, 'highpass', 1800, 0.7, 0.45, 0.001, 0.05, dest);
    [290, 470, 755, 1180, 1690].forEach((f, k) => tone(t, 'triangle', f, f * 0.99, 0.14 / (k + 1) + 0.03, 0.001, 0.3 + (4 - k) * 0.05, dest));
    burst(t + 0.02, 'bandpass', 2400, 2.5, 0.1, 0.01, 0.18, dest);
    tone(t + 0.06, 'sine', 72, 36, 0.75, 0.008, 0.34, dest);
    burst(t + 0.06, 'lowpass', 240, 1.1, 0.5, 0.01, 0.3, dest);
    burst(t + 0.14, 'lowpass', 420, 0.8, 0.26, 0.12, 0.9, dest, 0.6);
    // the engine digs in for it, and comes back
    if (loops._pumpEng) {
      const f = loops._pumpEng.frequency;
      f.setTargetAtTime((pumpRpm * 0.92) / 120, t + 0.05, 0.06);
      f.setTargetAtTime(pumpRpm / 120, t + 0.4, 0.35);
    }
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
    musicGain.gain.value = musicOn ? 0.55 * musicVol : 0;
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
    if (ac) { musicBus(); musicWant = on ? 0.55 * musicVol : 0; musicGain.gain.setTargetAtTime(musicWant, ac.currentTime, 0.3); }
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
    // it strokes while the concrete's going somewhere: your hose open, the pump driver on it, a co-worker pouring
    const flowing = pumpRun && (streamOn || (gs.tools.hose && gs.tools.hose.in === 'pumpman') || [...net.crew.values()].some((c) => c.act === 'pour'));
    pumpRpm = flowing ? 1650 : pumpRun ? 1150 : 800;
    loopTo('pump', (pumpRun ? 0.3 : pumpIdle ? 0.1 : 0) * near(pump.position.x, pump.position.z, 60) * muffle, pump.position.x, pump.position.z);
    if (loops._pumpEng && !flowing) loops._pumpEng.frequency.setTargetAtTime(pumpRpm / 120, ac.currentTime, 0.8);
    if (loops._pumpWhine) loops._pumpWhine.gain.setTargetAtTime(flowing ? 0.06 : pumpRun ? 0.02 : 0, ac.currentTime, 0.3);
    if (flowing && ac.currentTime > strokeAt) {
      strokeAt = ac.currentTime + (gs.mixState === 'stiff' ? 2.3 : gs.mixState === 'soup' ? 1.6 : 1.9) + rnd(-0.08, 0.08);
      const lv = near(pump.position.x, pump.position.z, 70) * muffle;
      if (lv > 0.02 && soundOn) pumpStroke(pump.position.x, pump.position.z, 0.55 * lv);
    }
    // the mixer ticks over while it waits; emptying, it revs, the drum turns fast and the chute runs
    const mixHere = live && mixer.visible, emptying = mixHere && flowing;
    loopTo('mixer', mixHere ? (emptying ? 0.36 : 0.22) * near(mixer.position.x, mixer.position.z, 50) * muffle : 0, mixer.position.x, mixer.position.z);
    if (loops._mixEng) loops._mixEng.frequency.setTargetAtTime((emptying ? 1350 : 720) / 120, ac.currentTime, 1.2);
    if (loops._drum) loops._drum.frequency.setTargetAtTime(emptying ? 0.55 : 0.12, ac.currentTime, 1.5);
    if (loops._drumWhine) loops._drumWhine.frequency.setTargetAtTime(emptying ? 240 : 140, ac.currentTime, 1.5);
    if (loops._chute) loops._chute.gain.setTargetAtTime(emptying ? 0.5 : 0, ac.currentTime, 0.4);
    if (emptying && chance(dt * 6)) sfx('stone', mixer.position.x, mixer.position.z);
    const running = live && mpos.on && input.action && lastCtxKind === 'trowel';
    const ride = gs.tool === 'rideOn', small = gs.tool === 'trowelSmall';
    loopTo('trowel', live && mpos.on && !gs.fitting ? (running ? 0.32 : 0.12) * (ride ? 1.4 : 1) : 0, mpos.x, mpos.z);
    // revs up under the throttle, and sags when the disc digs into soft concrete
    const lug = running && mpos.on ? digFactor() * 0.25 : 0;
    const rpm = (running ? 96 * (1 - lug) : 58) * (ride ? 0.72 : small ? 1.25 : 1);
    if (loops._trowelOsc) loops._trowelOsc.frequency.setTargetAtTime(rpm, ac.currentTime, 0.25);
    if (loops._trowelOsc2) loops._trowelOsc2.frequency.setTargetAtTime(rpm / 2, ac.currentTime, 0.25);
    if (loops._trowelFire) loops._trowelFire.frequency.setTargetAtTime(rpm * 0.42, ac.currentTime, 0.25);
    if (loops._trowelHiss) loops._trowelHiss.gain.setTargetAtTime(running ? (fitted() === 'pans' ? 0.5 : 0.28) : 0, ac.currentTime, 0.1);
    if (loops._trowelClatter) loops._trowelClatter.gain.setTargetAtTime(running && fitted() === 'blades' ? 0.18 : 0, ac.currentTime, 0.1);
    loopTo('pour', live && streamOn ? 0.45 : 0);
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
      const want = musicOn ? (performance.now() < duckUntil ? 0.18 : 0.55) * musicVol : 0;
      if (want !== musicWant) { musicWant = want; musicGain.gain.setTargetAtTime(want, ac.currentTime, 0.3); }
    }
    // something driving in: revving up the road, easing off to park; the mixer comes in backwards,
    // beeping the whole way
    const dv = drives[0];
    loopTo('engine', live && dv ? 0.26 * Math.max(0.15, near(dv.group.position.x, dv.group.position.z, 70)) * muffle : 0, dv ? dv.group.position.x : undefined, dv ? dv.group.position.z : undefined);
    if (dv && loops._driveEng) loops._driveEng.frequency.setTargetAtTime((dv.t < 0.75 ? 1400 : 700) / 120, ac.currentTime, 0.5);
    if (live && dv && dv.group === mixer && performance.now() > beepAt) { beepAt = performance.now() + 950; sfx('beep1', dv.group.position.x, dv.group.position.z); }
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
  const VOICES = {
    manager: [0.86, 1.12], foreman: [0.95, 0.92], pump: [0.84, 0.96], truck: [0.9, 1.0], alien: [1.9, 0.75], kid: [1.3, 1.06], plant: [1.08, 1.04],
    me: [0.86, 0.97], son: [1.28, 1.06], daughter: [1.34, 1.04], mum: [1.12, 0.96], partner: [1.05, 1.03], bank: [0.9, 0.95], hr: [1.08, 1.08], client: [1.0, 1.06], radio: [1.0, 1.1], neighbour: [0.92, 1.0],
    dentist: [1.04, 1.0], physio: [0.96, 1.0], gym: [1.12, 1.12], spam: [0.94, 1.12],
  };
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
  // The player is a man who pours concrete for a living, and sounds like one: a man's voice, pitched
  // low and a touch slow, and the same one every day (or the one picked in the settings).
  const GENDER = { me: 'm', partner: 'f', son: 'm', daughter: 'f', manager: 'm', foreman: 'm', pump: 'm', truck: 'm', helper: 'm', plant: 'f', alien: '', kid: '', mum: 'f', radio: 'm' };
  // who gets first pick of the voices: the ones heard all day long. When there aren't enough to go
  // round, a newcomer shares with somebody who isn't one of them if a voice of the right kind
  // allows, and with one of them only after that — always at a different pitch, never the player's.
  // They are cast in this order as soon as the phone's voices are known, not in the order they
  // happen to speak: whoever talks first in the morning can't walk off with the manager's voice.
  const LEADS = ['me', 'manager', 'foreman', 'partner', 'helper', 'pump', 'truck', 'mum', 'plant', 'radio', 'son', 'daughter'];
  const SHIFTS = [0, -0.13, 0.13, -0.22, 0.22, -0.28, 0.28];
  // A text-to-speech voice pushed far up or down, or hurried, stops sounding like a person and
  // starts sounding like a robot. Everybody stays inside this — the alien is the one exception.
  const PITCH_LO = 0.8, PITCH_HI = 1.38, RATE_LO = 0.88, RATE_HI = 1.14;
  let leadsCast = false;
  // and these few are nobody else's at all while any other voice of the right kind is left to share
  const OWN = ['me', 'manager', 'foreman', 'partner', 'helper'];
  let voiceBook = null, voiceBookAt = 0; // the voices on offer: [{ n: name, l: language, g: 'f' | 'm' | '' }]
  let cast = {};                   // who speaks with which today
  let castShift = {};              // how far off the voice's own pitch, for somebody sharing it
  let dayGender = {};              // the bank, the dentist, the neighbour: a man one day, a woman the next
  function voicesOnOffer() {
    // an engine that comes up later (Google's, beside the phone's own) brings more: look again now and then
    const now = performance.now();
    if (voiceBook && voiceBook.length && now - voiceBookAt < 15000) return voiceBook;
    voiceBookAt = now;
    let list = [];
    try {
      if (appBridge && typeof appBridge.voices === 'function') list = JSON.parse(appBridge.voices() || '[]');
      else if (window.speechSynthesis) {
        list = window.speechSynthesis.getVoices().filter((v) => /^en/i.test(v.lang))
          .map((v) => ({ n: v.name, l: v.lang, g: /female|woman/i.test(v.name) ? 'f' : /\bmale\b|\bman\b/i.test(v.name) ? 'm' : '' }));
      }
    } catch (e) { list = []; }
    if (Array.isArray(list) && list.length >= (voiceBook ? voiceBook.length : 0)) voiceBook = list;
    return voiceBook || [];
  }
  function genderOf(key) {
    if (key in GENDER) return GENDER[key];
    if (!dayGender[key]) dayGender[key] = chance(0.5) ? 'm' : 'f';
    return dayGender[key];
  }
  function castFor(key, g, p) {
    if (cast[key]) return cast[key];
    const book = voicesOnOffer();
    if (!book.length) return '';   // the engine isn't up yet: cast them on their next line
    if (!leadsCast) {
      leadsCast = true;
      LEADS.forEach((k) => { if (!cast[k]) castOne(k, genderOf(k), (VOICES[k] || [1])[0], book); });
      if (cast[key]) return cast[key];
    }
    return castOne(key, g, p, book);
  }
  function castOne(key, g, p, book) {
    p = p || 1;
    const mine = store('pourday.myVoice');
    if (key === 'me' && mine && book.some((v) => v.n === mine)) return settle('me', mine, p);
    const taken = new Set(Object.values(cast));
    if (key !== 'me' && mine) taken.add(mine);      // nobody else gets to sound like you
    const leads = new Set(LEADS.map((k) => cast[k]).filter(Boolean));
    if (mine) leads.add(mine);
    const own = new Set(OWN.filter((k) => k !== key).map((k) => cast[k]).filter(Boolean));
    // a co-worker, a real one on another phone, is somebody too
    Object.keys(cast).forEach((k) => { if (k !== key && k.startsWith('crew')) own.add(cast[k]); });
    if (mine && key !== 'me') own.add(mine);
    const fits = (v) => !g || v.g === g, notMine = (v) => key === 'me' || v.n !== mine;
    // A man's voice for a man and a woman's for a woman, always: sharing one (at another pitch)
    // comes before borrowing the other kind, which only happens on a phone that has none.
    // no more than three to a voice while there's another of the right kind to share: past that,
    // even at different pitches they start to sound like one man doing all the voices
    const usesOf = (n) => Object.values(cast).filter((x) => x === n).length, roomy = (v) => usesOf(v.n) < 3;
    const pools = [
      book.filter((v) => fits(v) && !taken.has(v.n)),
      book.filter((v) => fits(v) && !leads.has(v.n) && roomy(v)),
      book.filter((v) => fits(v) && !own.has(v.n) && roomy(v)),
      book.filter((v) => fits(v) && notMine(v) && roomy(v)),
      book.filter((v) => fits(v) && notMine(v)),
      book.filter((v) => !v.g && !taken.has(v.n)),
      book.filter((v) => !v.g && notMine(v)),
      book.filter((v) => !leads.has(v.n)),
      book.filter(notMine),
      book,
    ];
    // the least shared of what's left, so a crowd spreads over all the voices there are
    const pool = pools.find((pl) => pl.length);
    const uses = (n) => Object.values(cast).filter((x) => x === n).length;
    const least = Math.min(...pool.map((v) => uses(v.n)));
    const name = pick(pool.filter((v) => uses(v.n) === least)).n;
    if (key === 'me' && !mine) store('pourday.myVoice', name);
    return settle(key, name, p);
  }
  /**
   * Gives [key] voice [name] at pitch [p]; sharing it with somebody, the pitch is moved until the two
   * are clearly apart — by where they end up, not by how far each was moved.
   */
  let castPitch = {};
  function settle(key, name, p) {
    const others = Object.keys(cast).filter((k) => k !== key && cast[k] === name).map((k) => castPitch[k]);
    // a child is only ever moved up or a little down: pushed low, a kid is just a short man
    const lo = /^(kid|son$|daughter$|ballKid|droneKid)/.test(key) ? 1.16 : PITCH_LO;
    const at = (x) => clamp(p + x, lo, PITCH_HI);
    const gap = (x) => others.reduce((g, o) => Math.min(g, Math.abs(at(x) - o)), 9);
    let shift = SHIFTS.find((x) => gap(x) >= 0.12);
    if (shift === undefined) {
      // crowded: the pitch furthest from all of them
      shift = 0;
      for (let x = -0.3; x <= 0.3; x += 0.02) if (gap(x) > gap(shift)) shift = x;
    }
    castShift[key] = shift;
    castPitch[key] = at(shift);
    cast[key] = name;
    return name;
  }
  function newCast() { cast = {}; castShift = {}; castPitch = {}; dayGender = {}; kidVoices = {}; voiceBook = null; leadsCast = false; }
  /** A line from a list, not one used lately: the list is gone through before anything comes round again. */
  const usedLines = new Set();
  function fresh(list) {
    let left = list.filter((l) => !usedLines.has(l));
    if (!left.length) { list.forEach((l) => usedLines.delete(l)); left = list; }
    const l = pick(left);
    usedLines.add(l);
    return l;
  }
  const saidLog = [];              // what was said lately, for the tests
  // Who goes first when lines are waiting: somebody with a real line (2), then the player's own
  // mutter (1); the player reading the notes out (0) never waits — nor cuts in on anybody.
  let lastSayAt = -1e9;
  /** Whether anything can be heard: the app's engine up with a voice in it, or the browser's. */
  function canVoice() {
    try {
      // the app says when its engine is up; an older one only by having named voices to offer
      if (appBridge && typeof appBridge.canSpeak === 'function') return !!appBridge.canSpeak();
      if (appBridge && typeof appBridge.speakAs === 'function') return voicesOnOffer().length > 0;
      if (appBridge && typeof appBridge.speak === 'function') return true;
      return !!(window.speechSynthesis && window.speechSynthesis.getVoices().length);
    } catch (e) { return false; }
  }
  /** Says a line out loud; true if it was. [prio] as above — 2 unless said otherwise. */
  // Lines wait their turn. A new line used to go straight out and cut off whoever was still
  // talking (the phone flushes whatever it's saying), which with a site this chatty was often.
  // Now it waits in a short queue until the phone says the last one is finished; the notes read
  // out in your own voice don't wait, they'd be old news — they stay on the screen instead.
  const sayQ = [];
  let talking = false, talkingUntil = 0, sayN = 0;
  const tellsDone = (() => { try { return !!(appBridge && typeof appBridge.tellsDone === 'function' && appBridge.tellsDone()); } catch (e) { return false; } })();
  function busyTalking() { const now = performance.now(); return now < duckUntil || (talking && now < talkingUntil); }
  /** The phone has finished the line (or it was cut off): the next one can go, after a breath. */
  function voiceDone() {
    talking = false;
    duckUntil = Math.min(duckUntil, performance.now() + 250);
  }
  window.pdVoice = { done: voiceDone };
  function pumpSay() {
    if (!sayQ.length || busyTalking()) return;
    const q = sayQ.shift();
    if (performance.now() - q.at > 9000) { pumpSay(); return; }   // stale by now: somebody else's moment
    speakNow(q.text, q.who, q.prio, q.line);
  }
  /** Says a line out loud; true if it was, or will be when it's its turn. [prio] as above — 2 unless said otherwise. */
  function say(text, who, prio) {
    prio = prio === undefined ? 2 : prio;
    if (!voicesOn || !text || netRemote || netCapture || !canVoice()) return false;
    const line = spoken(text);
    if (!line) return false;
    if (busyTalking() || sayQ.length) {
      if (prio === 0) return false;
      if (sayQ.some((q) => q.line === line)) return true;
      sayQ.push({ text, who, prio, line, at: performance.now() });
      sayQ.sort((x, y) => y.prio - x.prio || x.at - y.at);
      if (sayQ.length > 3) sayQ.length = 3;
      return true;
    }
    return speakNow(text, who, prio, line);
  }
  function speakNow(text, who, prio, line) {
    const now = performance.now();
    let p = 1, r = 1, key = '', g = '';
    if (Array.isArray(who)) [p, r] = who;
    else if (who && typeof who === 'object') ({ p, r, key, g } = who);
    else if (VOICES[who]) { [p, r] = VOICES[who]; key = who; g = genderOf(who); }
    const name = key ? castFor(key, g, p) : '';
    const sh = (key && castShift[key]) || 0;
    if (key && castPitch[key]) p = castPitch[key];
    if (sh) r += sh > 0 ? 0.04 : -0.04;
    if (key !== 'alien') { p = clamp(p, PITCH_LO, PITCH_HI); r = clamp(r, RATE_LO, RATE_HI); }
    saidLog.push(line);
    if (saidLog.length > 40) saidLog.shift();
    // about 14 letters a second at normal pace: a guess, until the phone says it's done
    const est = (line.length * 72) / r + 700;
    duckUntil = now + est;
    talking = true;
    talkingUntil = now + (tellsDone ? est * 2.5 + 3000 : est);
    lastSayAt = now;
    speakerShow(who, prio);
    try {
      if (appBridge && typeof appBridge.speakAs === 'function') { appBridge.speakAs(line, p, r, name); return true; }
      if (appBridge && typeof appBridge.speak === 'function') { appBridge.speak(line, p, r); return true; }
      if (window.speechSynthesis && window.SpeechSynthesisUtterance) {
        const u = new SpeechSynthesisUtterance(line);
        u.lang = 'en-GB'; u.pitch = p; u.rate = r;
        const vv = name && window.speechSynthesis.getVoices().find((x) => x.name === name);
        if (vv) u.voice = vv;
        const n = ++sayN;
        u.onend = () => { if (n === sayN) voiceDone(); };
        window.speechSynthesis.cancel();
        window.speechSynthesis.speak(u);
      }
    } catch (e) { /* no voice to be had: the line is on the screen anyway */ }
    return true;
  }
  /**
   * The notes read out in the player's own voice, as the player's own thoughts: "Your boots are
   * printing" is heard as "My boots are printing". Only for the ear — it needn't be grammar-proof,
   * just better than a stranger reading your day to you.
   */
  const OBJ_BEFORE = /\b(at|to|for|with|about|from|on|off|behind|past|towards?|near|than|like|into|onto|around|round|over|under|without|beside|against|after|before|by|of|upon|through|tells?|told|gives?|gave|asks?|asked|sees?|saw|watch(?:es)?|follows?|charges?|pays?|bites?|licks?|ignores?|thanks?|hates?|loves?|reminds?|calls?|called|hears?|lets?|helps?|wants?|needs?|finds?|hits?|miss(?:es)?|throws?|gets?|got|makes?|made|sends?|sent|shows?|hands?|warns?|beats?|pulls?|push(?:es)?|stops?|trips?|drags?|grabs?|bills?|docks?|fires?|owes?|takes?|keeps?|leaves?|left|joins?|meets?|greets?|judges?|blames?|trusts?|notices?|spots?|splash(?:es)?|soaks?|wakes?|bless(?:es)?|kills?|nearly|saves?|catch(?:es)?|caught)\s+you\b/gi;
  function firstPerson(t) {
    return String(t)
      .replace(/\b(thank) you\b/gi, '$1 ~u~')
      .replace(OBJ_BEFORE, (m) => m.slice(0, -3) + 'me')
      .replace(/\bare you\b/g, 'am I').replace(/\bAre you\b/g, 'Am I')
      .replace(/\byou are\b/gi, (m) => (m[0] === 'Y' ? 'I am' : 'I am'))
      .replace(/\byou were\b/gi, 'I was').replace(/\byou weren't\b/gi, 'I wasn\'t').replace(/\byou aren't\b/gi, 'I\'m not')
      .replace(/\byou're\b/gi, 'I\'m').replace(/\byou've\b/gi, 'I\'ve').replace(/\byou'll\b/gi, 'I\'ll').replace(/\byou'd\b/gi, 'I\'d')
      .replace(/\byourself\b/g, 'myself').replace(/\bYourself\b/g, 'Myself')
      .replace(/\byours\b/g, 'mine').replace(/\bYours\b/g, 'Mine')
      .replace(/\byour\b/g, 'my').replace(/\bYour\b/g, 'My')
      .replace(/\byou\b/g, 'I').replace(/\bYou\b/g, 'I')
      .replace(/~u~/g, 'you');
  }
  function hush() {
    duckUntil = 0;
    sayQ.length = 0;
    talking = false;
    try {
      if (appBridge && typeof appBridge.hush === 'function') appBridge.hush();
      else if (window.speechSynthesis) window.speechSynthesis.cancel();
    } catch (e) { /* nothing was talking */ }
  }
  function setVoices(on) { voicesOn = on; store('pourday.voices', on ? 'on' : 'off'); if (!on) hush(); }
  /** A person's voice, kept for the whole of their visit: their own from the phone's, a little higher or lower. */
  let personN = 0;
  function personVoice(g, kidG) {
    if (g === 'kid') return kidVoice('kid' + (++personN), kidG);
    return { p: rnd(0.88, 1.16), r: rnd(0.95, 1.08), key: 'person' + (++personN), g: g || '' };
  }
  /**
   * A child's voice: a boy's or a girl's, pitched up and quick, and nobody else's — the kid with the
   * ball, the one with the drone and the neighbour's all sound like themselves. Kept for the day.
   */
  let kidVoices = {};
  function kidVoice(key, g) {
    if (!kidVoices[key]) {
      const girl = g ? g === 'f' : chance(0.5);
      kidVoices[key] = { p: girl ? rnd(1.26, 1.38) : rnd(1.18, 1.32), r: rnd(1.02, 1.1), key, g: girl ? 'f' : 'm' };
    }
    return kidVoices[key];
  }

  // ------------------------------------------------------------------ the phone in your hand
  // A text or a call and you take the phone out: it comes up from the bottom of the screen in your
  // hand, buzzes, shows who it is and what they want — typing dots, then the message under the last
  // two they sent you; a call rings, you pick up, and the words come up as they're said — and it
  // goes back in your pocket.
  const phoneEl = $('#phone'), phApp = $('#phApp');
  const phoneQ = [], phoneThreads = {};
  let phoneOn = null, phoneTimer = 0;
  const AV_COL = ['#e5484d', '#3e8ed0', '#2fa65a', '#a95fd0', '#d9a200', '#ff6b1a', '#1fa89c', '#d6457a'];
  const escHtml = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;');
  function avatarCol(name) { let h = 0; for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) | 0; return AV_COL[Math.abs(h) % AV_COL.length]; }
  function initials(name) { return name.replace(/^(The|Your|A|An) /i, '').split(/\s+/).map((w) => w[0]).join('').slice(0, 2).toUpperCase(); }
  function phoneText(from, text, voice) { phoneQ.push({ kind: 'text', from, text, voice }); phoneNext(); }
  function phoneCall(from, text, voice) { phoneQ.push({ kind: 'call', from, text, voice }); phoneNext(); }
  function phoneReset() { phoneQ.length = 0; phoneOn = null; clearTimeout(phoneTimer); phoneEl.classList.remove('up', 'buzz'); }
  function phoneBar() {
    $('#phTime').textContent = clock(gs.t);
    // it was charged overnight, in theory
    const bat = clamp(100 - (gs.t - 330) / 7, 3, 100), b = $('#phBat');
    b.style.width = `${Math.round(bat * 0.82)}%`;
    b.parentNode.classList.toggle('low', bat < 20);
  }
  function phoneNext() {
    if (phoneOn || !phoneQ.length) return;
    // on the ground: it buzzes down there, and it waits for you to pick it up
    if (gs.phoneDown) {
      if (!gs.phoneDown.rang) { gs.phoneDown.rang = true; sfx(phoneQ[0].kind === 'call' ? 'ring' : 'buzz', gs.phoneDown.x, gs.phoneDown.z); }
      toastOnce('phoneGround', 'Your phone is buzzing on the ground where you dropped it.', 'warn', 45000);
      return;
    }
    const m = phoneOn = phoneQ.shift();
    phoneBar();
    const col = avatarCol(m.from), ini = escHtml(initials(m.from)), words = String(m.text).replace(/"/g, '');
    restartAnim(phoneEl, 'buzz');
    phoneEl.classList.add('up');
    if (m.kind === 'text') {
      sfx('buzz');
      const th = phoneThreads[m.from] || (phoneThreads[m.from] = []);
      const old = th.slice(-2).map((t) => `<div class="bub old">${escHtml(t.text)}<time>${t.at}</time></div>`).join('');
      phApp.innerHTML = `<div class="mhead"><span class="av" style="background:${col}">${ini}</span><div><b>${escHtml(m.from)}</b><small>mobile · now</small></div></div>`
        + `<div class="thread">${old}<div class="bub typing"><i></i><i></i><i></i></div></div><div class="mreply">Reply…</div>`;
      th.push({ text: words, at: clock(gs.t) });
      phoneTimer = setTimeout(() => {
        const t = phApp.querySelector('.typing');
        if (t) t.outerHTML = `<div class="bub">${escHtml(words)}<time>${clock(gs.t)}</time></div>`;
        say(m.text, m.voice);
        phoneTimer = setTimeout(phoneAway, clamp(2600 + words.length * 55, 3800, 9500));
      }, 1000);
      return;
    }
    sfx('ring');
    phApp.innerHTML = `<div class="call"><small class="cst">Incoming call…</small><div class="cav"><span class="av" style="background:${col}">${ini}</span><i></i><i></i></div>`
      + `<b class="cname">${escHtml(m.from)}</b><div class="wave">${'<b></b>'.repeat(14)}</div><div class="caption"></div><div class="cbtns"><i class="dec"></i><i class="acc"></i></div></div>`;
    const call = phApp.querySelector('.call'), cst = call.querySelector('.cst'), cap = call.querySelector('.caption');
    phoneTimer = setTimeout(() => {
      // you pick up; he's already talking
      call.classList.add('live');
      sfx('phoneYell');
      say(m.text, m.voice);
      const t0 = performance.now(), len = Math.max(2500, words.length * 62);
      const tick = () => {
        if (phoneOn !== m) return;
        const el = performance.now() - t0;
        cst.textContent = `00:${String(Math.floor(el / 1000)).padStart(2, '0')}`;
        cap.textContent = words.slice(0, Math.ceil((Math.min(el, len) / len) * words.length));
        if (el < len) { phoneTimer = setTimeout(tick, 90); return; }
        call.classList.remove('live');
        call.classList.add('ended');
        cst.textContent = 'Call ended';
        phoneTimer = setTimeout(phoneAway, 1300);
      };
      tick();
    }, 1700);
  }
  // Out of your pocket and onto the ground — or into the pour — face down, of course. It stays
  // there, ringing to itself, until you go back and pick it up.
  const phoneObj = new THREE.Group();
  box(0.079, 0.012, 0.159, 0xff6b1a, 0, 0, 0, phoneObj);                         // a rugged orange case: site phones have them
  box(0.066, 0.002, 0.144, 0x0d1822, 0, -0.006, 0, phoneObj);                   // the screen, face down
  box(0.024, 0.004, 0.03, 0x3a3d42, -0.02, 0.006, -0.05, phoneObj);             // the cameras
  phoneObj.visible = false;
  scene.add(phoneObj);
  const phoneFall = { t: 1, len: 0.6, x: 0, z: 0, y0: 1, y1: 0 };
  let phoneMarker = null;
  function dropPhone() {
    if (gs.phoneDown || inVanNow() || !['prep', 'pipes', 'pour', 'wash', 'cure'].includes(gs.phase)) return false;
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    const x = player.x + fx * 0.45 + rnd(-0.2, 0.2), z = player.z + fz * 0.45 + rnd(-0.2, 0.2);
    const c = cellAt(x, z), wet = !!(c && gs.pourStarted && c.fill > 20 && cellH(c) < 25);
    gs.phoneDown = { x, z, wet, at: gs.t };
    Object.assign(phoneFall, { t: 0, x, z, y0: EYE - 0.75, y1: groundY(x, z) + (wet ? 0.0 : 0.006), spin: rnd(4, 7) });
    phoneObj.visible = true;
    if (phoneMarker) { phoneMarker.x = x; phoneMarker.z = z; phoneMarker.group.position.set(x, 0, z); }
    if (phoneOn) { clearTimeout(phoneTimer); phoneQ.unshift(phoneOn); phoneEl.classList.remove('up'); phoneOn = null; }
    setTimeout(() => sfx(wet ? 'wet' : 'hard', x, z), 550);
    return true;
  }
  function updatePhoneDrop(dt) {
    if (!phoneObj.visible) return;
    if (!gs.phoneDown) { phoneObj.visible = false; return; }
    const f = phoneFall;
    f.t = Math.min(1, f.t + dt / f.len);
    const u = f.t;
    phoneObj.position.set(f.x, lerp(f.y0, f.y1, u * u), f.z);
    // tumbling down, and flat on its face when it lands; in wet concrete it settles in a little
    phoneObj.rotation.set(u < 1 ? u * f.spin : 0, 0.7, u < 1 ? u * f.spin * 0.6 : 0);
    if (u >= 1 && gs.phoneDown.wet) phoneObj.position.y = f.y1 - 0.004;
  }
  function pickUpPhone() {
    const d = gs.phoneDown;
    if (!d) return false;
    gs.phoneDown = null;
    phoneObj.visible = false;
    sfx('pickup');
    if (d.wet) {
      gs.phoneCracked = true;
      toast('You fish your phone out of the pour. Concrete in the charging port, concrete in the speaker, concrete in your future.', 'warn');
      charge('Phone, rescued from the pour. It still rings. Grey.', 45);
    } else if (chance(0.55)) {
      gs.phoneCracked = true;
      toast('Face down, of course. The screen is a map of the river delta now.', 'warn');
      charge('New phone screen. It met the site face first', 60);
    } else toast('Not a scratch on it. You don\'t trust it.', 'good');
    phoneEl.classList.toggle('cracked', !!gs.phoneCracked);
    setTimeout(phoneNext, 600);
    return true;
  }
  function phoneAway() {
    phoneEl.classList.remove('up');
    phoneTimer = setTimeout(() => { phoneOn = null; phoneNext(); }, 650);
  }

  // ------------------------------------------------------------------ who is talking
  // Anybody's voice, heard with the subtitles off, is a voice from nowhere. So while somebody
  // talks, a tag says who: with an arrow to them if they're on the site, a phone if they rang, a
  // house for the neighbour at the window.
  const SPEAKERS = {
    manager: ['The manager', 'phone'], foreman: ['The foreman', 'phone'], plant: ['The plant', 'phone'], mum: ['Mum', 'phone'], partner: ['Wife', 'phone'],
    son: ['Your son', 'phone'], daughter: ['Your daughter', 'phone'], bank: ['The bank', 'phone'], hr: ['HR', 'phone'], client: ['The client', 'phone'],
    dentist: ['The dentist', 'phone'], physio: ['The physio', 'phone'], gym: ['The gym', 'phone'], spam: ['Unknown number', 'phone'], neighbour: ['The neighbour', 'house'],
    pump: ['Pump driver', () => (pumpGuy.visible ? pumpGuy.position : pump.visible ? pump.position : null)],
    truck: ['Truck driver', () => (mixGuy.visible ? mixGuy.position : mixer.visible ? mixer.position : null)],
    radio: ['The radio', () => (pump.visible ? pump.position : mixer.visible ? mixer.position : null)],
    alien: ['???', () => (ufo.g.visible ? ufo.g.position : null)], kid: ['A kid', null],
  };
  let spk = null;
  function speakerShow(who, prio) {
    if (who === 'me' || !prio || modalOpen) return;
    let name = '', at = null;
    if (who && typeof who === 'object') { name = who.name || ''; at = who.at || null; }
    else if (SPEAKERS[who]) [name, at] = SPEAKERS[who];
    // on the phone in your hand already: that says who it is
    if (!name || (phoneOn && phoneOn.voice === who)) return;
    spk = { at };
    $('#spkName').textContent = name;
    const el = $('#speaker');
    el.hidden = false;
    restartAnim(el, 'pop');
    speakerDir();
  }
  function speakerDir() {
    if (!spk) return;
    const el = $('#speaker'), dir = $('#spkDir');
    if (performance.now() > duckUntil + 300) { el.hidden = true; spk = null; return; }
    const pos = typeof spk.at === 'function' ? spk.at() : null;
    dir.className = spk.at === 'phone' ? 'phone' : spk.at === 'house' ? 'house' : pos ? '' : 'voice';
    if (pos) {
      const dx = pos.x - player.x, dz = pos.z - player.z, c = Math.cos(player.yaw), sn = Math.sin(player.yaw);
      dir.style.setProperty('--dir', `${Math.atan2(dx * c - dz * sn, -dx * sn - dz * c)}rad`);
    }
  }

  // ------------------------------------------------------------------ your boots
  // Walk in the pour and it comes with you: on the boots, heavier with every step, printed grey
  // across the gravel, into the van if you let it. Washed at the tank; left, it sets on them.
  function bootsGet(a) {
    if (!gs || netRemote) return;
    // fresh concrete starts its own clock, on clean boots or on what set on them earlier
    if ((gs.boots || 0) - (gs.bootsFloor || 0) < 0.05) gs.bootsAt = gs.t;
    gs.boots = Math.min(1, (gs.boots || 0) + a);
    if (gs.boots > 0.35) toastOnce('bootsHeavy', fresh(L.bootsHeavy), 'warn', 240000);
  }
  function vanFloor() {
    if ((gs.boots || 0) < 0.2 || gs.vanFloor) return;
    gs.vanFloor = true;
    toast('You climb into the van in those boots. The van floor is concrete now too.', 'warn');
    charge('Concrete footprints on the van floor', 15);
  }
  function bootsRow() { return (gs.boots || 0) < 0.05 ? 'clean, somehow' : bootsSet() ? 'set solid. A new pair' : gs.bootsFloor && gs.boots < gs.bootsFloor + 0.05 ? 'washed, but grey for ever' : (gs.boots || 0) > 0.5 ? 'concrete to the ankles' : 'grey'; }
  function bootsSet() { return (gs.boots || 0) - (gs.bootsFloor || 0) > 0.15 && gs.t - (gs.bootsAt || gs.t) > 150; }
  function inVanNow() { return gs.waitMode === 'van'; }
  const trackPrints = new THREE.Group();
  scene.add(trackPrints);
  // a boot's sole, lugs and all, not a grey leaf the size of a shoebox
  const soleTex = (function () {
    const c = document.createElement('canvas');
    c.width = 64; c.height = 160;
    const g = c.getContext('2d');
    g.fillStyle = '#fff';
    g.beginPath(); g.ellipse(32, 46, 25, 42, 0, 0, Math.PI * 2); g.fill();     // the forefoot
    g.beginPath(); g.ellipse(32, 128, 19, 28, 0, 0, Math.PI * 2); g.fill();    // the heel
    g.fillRect(19, 70, 26, 50);                                                 // the waist between
    g.globalCompositeOperation = 'destination-out';
    g.strokeStyle = '#000'; g.lineWidth = 4;
    for (let y = 10; y < 156; y += 13) { g.beginPath(); g.moveTo(6, y); g.lineTo(32, y + 7); g.lineTo(58, y); g.stroke(); }
    return new THREE.CanvasTexture(c);
  })();
  const trackMat = new THREE.MeshLambertMaterial({ color: 0x7e8182, alphaMap: soleTex, transparent: true, opacity: 0.85, depthWrite: false });
  const trackGeo = new THREE.PlaneGeometry(0.12, 0.3);
  let trackSide = 1;
  function trackPrint(x, z, yaw) {
    trackSide = -trackSide;
    const p = trackPrints.children.length >= 90 ? trackPrints.children[0] : new THREE.Mesh(trackGeo, trackMat);
    if (p.parent) trackPrints.remove(p);
    // where the foot was: a step to the side, a little behind the eyes
    const sx = Math.cos(yaw) * 0.12 * trackSide + Math.sin(yaw) * 0.12, sz = -Math.sin(yaw) * 0.12 * trackSide + Math.cos(yaw) * 0.12;
    p.position.set(x + sx, groundY(x + sx, z + sz) + 0.012, z + sz);
    p.rotation.set(-Math.PI / 2, 0, yaw + rnd(-0.12, 0.12));
    trackPrints.add(p);
  }
  // your own, at the bottom of the view when you look down: the boots, and what's on them
  const myBoots = new THREE.Group();
  scene.add(myBoots);
  const bootCrust = [];
  const myFeet = [-1, 1].map((sd) => {
    const f = new THREE.Group();
    f.position.x = sd * 0.13;
    box(0.13, 0.022, 0.31, 0x151311, 0, 0.011, -0.03, f);          // the sole, cleated
    box(0.12, 0.075, 0.27, 0x2d2823, 0, 0.055, -0.02, f);          // the upper
    box(0.115, 0.05, 0.08, 0x3b3530, 0, 0.06, -0.15, f).rotation.x = 0.35; // toecap
    box(0.1, 0.12, 0.1, 0x2d2823, 0, 0.14, 0.07, f);               // the ankle
    box(0.104, 0.018, 0.104, 0xd9b44a, 0, 0.19, 0.07, f);          // the collar, yellow stitching
    const cr = new THREE.Group();
    box(0.135, 0.06, 0.2, 0x9b9d9a, 0, 0.035, -0.09, cr);
    mesh(new THREE.SphereGeometry(0.05, 8, 6), 0x9a9c99, 0.03, 0.07, -0.12, cr).scale.set(1.2, 0.6, 1);
    mesh(new THREE.SphereGeometry(0.04, 8, 6), 0x8f918e, -0.05, 0.1, 0.02, cr).scale.set(0.8, 0.9, 1.1);
    mesh(new THREE.SphereGeometry(0.035, 8, 6), 0x9a9c99, 0.05, 0.12, 0.06, cr);
    cr.visible = false;
    f.add(cr);
    bootCrust.push(cr);
    myBoots.add(f);
    return f;
  });
  function updateMyBoots() {
    const on = gs.phase !== 'title' && gs.phase !== 'end' && !inVanNow() && gs.tool !== 'rideOn' && player.fall <= 0;
    myBoots.visible = on;
    if (!on) return;
    myBoots.position.set(player.x, groundY(player.x, player.z), player.z);
    myBoots.rotation.y = player.yaw;
    const stuck = performance.now() < gs.stuckUntil;
    myFeet.forEach((f, k) => {
      const ph = player.bob + k * Math.PI;
      f.position.z = -0.1 + (player.moving ? Math.sin(ph) * 0.12 : 0);
      f.position.y = player.moving ? Math.max(0, Math.cos(ph)) * 0.035 : 0;
      if (stuck && k === 0) f.position.y = -0.06;
      // from foot to foot, when it's that urgent
      const w = urgePiss();
      if (w > 0 && !player.moving && !CALM) f.position.y = Math.max(0, Math.sin(performance.now() / 1000 * 5.2 + k * Math.PI)) * 0.045 * w;
    });
    const b = gs.boots || 0;
    bootCrust.forEach((cr) => { cr.visible = b > 0.05; cr.scale.set(1, 0.5 + b, 1); });
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
  /** Everything solid: the yard's furniture (turned any way), the trucks, and on an inside day the walls. */
  function obstacles() {
    const list = layoutObs.slice();
    if (pump.visible) { const w = day.boom ? 2.9 : 1.3; list.push({ cx: pump.position.x - 0.1, cz: pump.position.z, hx: 4.5, hz: w, ang: 0 }); }
    if (mixer.visible) list.push({ cx: mixer.position.x - 0.1, cz: mixer.position.z, hx: 4.7, hz: 1.3, ang: 0 });
    if (hall.visible) hallWalls.forEach((w) => list.push(w));
    return list;
  }
  function collide(x, z) {
    x = clamp(x, -43.5, 43.5);
    z = clamp(z, -33.5, 33.5);
    const pad = 0.35;
    for (const o of obstacles()) {
      // into the thing's own frame, out along the shortest way, and back
      let [lx, lz] = turnXZ(x - o.cx, z - o.cz, -o.ang);
      const ex = o.hx + pad, ez = o.hz + pad;
      if (Math.abs(lx) >= ex || Math.abs(lz) >= ez) continue;
      if (ex - Math.abs(lx) < ez - Math.abs(lz)) lx = Math.sign(lx || 1) * ex; else lz = Math.sign(lz || 1) * ez;
      const [wx, wz] = turnXZ(lx, lz, o.ang);
      x = o.cx + wx; z = o.cz + wz;
    }
    return [x, z];
  }

  // ------------------------------------------------------------------ marks on the concrete
  // A mark is kept as where it is and how deep it went, and drawn over the surface every time the
  // surface is redrawn — so a machine wearing it down shows it fading, pass by pass, not all at once.
  /** How deep a print goes: to the laces in fresh concrete, a dent at 60%. */
  function markDepth(c) { return clamp((65 - (c ? cellH(c) : gs.H)) / 45, 0.3, 1); }
  function stamp(kind, x, z, rot, silent, scale) {
    const c = cellAt(x, z);
    if (!c || !gs.poured || cellH(c) >= 60 || c.fill < 20) return false;
    // enough to read as trampled; more would only make every redraw slower
    if (c.marks.length >= 16) return false;
    c.marks.push({ kind, x, z, rot: rot || 0, depth: markDepth(c) * (scale || 1) });
    surfDirty = true;
    if (!silent) gs.stats.prints++;
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
  /** What's in the slab that counts: every square up to its thickness, none of the heap above it. */
  function pouredIn() { return gs.cells.reduce((s2, c) => s2 + Math.min(c.fill, day.thick), 0) / 1000; }
  function filledShare() { return gs.cells.filter((c) => c.fill >= day.thick - 10).length / gs.cells.length; }
  function rms() {
    let s = 0;
    gs.cells.forEach((c) => { const d = c.fill - day.thick; s += d * d; });
    return Math.sqrt(s / gs.cells.length);
  }
  function laserWorks() { return gs.prep.laser && gs.laserBattery; }
  /** Every corner checked: the laser is set. A board still out stays out, and shows in the slab. */
  function laserMaybeReady() {
    if (gs.laserSetup !== 3 || site.levelChecks.some((c) => !c.done)) return;
    gs.laserSetup = 4;
    gs.prep.laser = true;
    const out = site.levelChecks.filter((c) => Math.abs(c.off) > 4 && !c.fixed);
    toast(out.length ? `Laser set. The ${out[0].name} board is still ${Math.abs(out[0].off)} mm out — knock it before the pour, or the slab follows it.` : 'Laser set, boards on height. Now it\'s a laser, not a spinning ornament.', out.length ? 'warn' : 'good');
  }
  /** Boards left out of height when the pour starts: the screed follows them, so the slab does too. */
  function boardsOut() {
    if (gs.boardsApplied || !site.levelChecks) return;
    gs.boardsApplied = true;
    site.levelChecks.filter((c) => Math.abs(c.off) > 4 && !c.fixed).forEach((c) => {
      gs.cells.forEach((cell) => {
        const d = hyp(gx(cell.i) + 0.5, gz(cell.j) + 0.5, c.x, c.z);
        if (d < 3) cell.fill += c.off * (1 - d / 3);
      });
      remember(`The ${c.name} board was ${Math.abs(c.off)} mm ${c.off < 0 ? 'low' : 'high'} and nobody fixed it. The slab ${c.off < 0 ? 'dips' : 'rises'} to meet it.`);
    });
    cellsDirty = true;
  }
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
  function machineTool() { return (gs.phase === 'cure' || gs.phase === 'wash' || gs.phase === 'pour') && isMachine(gs.tool) ? gs.tool : null; }
  function fitted() { return isMachine(gs.tool) ? gs.fit[gs.tool] : null; }
  function passKey() { return fitted() === 'blades' ? 'covB' : 'covP'; }

  // the shape and the colour of the slab
  let cellsDirty = true;
  // fresh concrete is dark and wet-looking against the pale stone base, so every pour shows
  const WET = new THREE.Color(0.34, 0.355, 0.375), DRY = new THREE.Color(0.97, 0.97, 0.96);
  const DEV = { ok: new THREE.Color(0x4fae6a), hi: new THREE.Color(0xe0873a), vhi: new THREE.Color(0xd8392f), lo: new THREE.Color(0x5aa9ff), vlo: new THREE.Color(0x2f6fd0) };
  function paintCells() {
    const laser = gs.laserOn && laserWorks() && !gs.pourDone;
    const base = tmpC.copy(WET).lerp(DRY, clamp(gs.H / 70, 0, 1));
    const devC = new THREE.Color();
    const loads = gs.loads.some((l) => l && l.at !== null);
    for (const k of slabVerts) {
      const x = slabPos.getX(k), z = slabPos.getZ(k);
      const f = fillAt(x, z);
      slabPos.setY(k, surfY(f));
      if (loads) base.copy(WET).lerp(DRY, clamp(hAt(x, z) / 70, 0, 1));
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
    // wet concrete gleams, drying concrete goes matt, and the blades bring the shine back. Mid-pour
    // the first trucks' end has gone matt while the rest is wet: the gleam is the wet share of it.
    let fresh = 1;
    if (!gs.poured) {
      let n = 0, f = 0;
      for (const c of gs.cells) if (c.fill > 20) { n++; if (cellH(c) < 20) f++; }
      if (n) fresh = f / n;
    }
    const shine = !gs.poured ? 0.3 + 0.7 * fresh : gs.H < 30 ? 1 - gs.H / 40 : 0.25;
    const blade = Math.min(3, gs.bladePasses.length);
    slabMat.shininess = gs.poured && gs.H >= 30 ? 8 + blade * 16 : !gs.poured ? lerp(12, 70, fresh) : 70;
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
    // mid-pour, only the part that's in, and of that only what has gone off enough to carry it
    const inn = (c) => gs.poured || c.fill > 20;
    if (tool) {
      const key = passKey(), soft = fitted() === 'blades' ? 50 : 20;
      view.globalAlpha = 1;
      for (const c of gs.cells) {
        if (c[key] || !inn(c)) continue;
        // orange: still to do; blue: too soft for it yet
        view.fillStyle = cellH(c) < soft ? 'rgba(90,169,255,0.2)' : 'rgba(255,150,40,0.16)';
        view.fillRect(c.i * PPM, c.j * PPM, PPM, PPM);
      }
      view.fillStyle = 'rgba(255,150,40,0.35)';
      for (const c of gs.cells) {
        if (c[key] || !inn(c) || cellH(c) < soft) continue;
        // a thin rim on the edge of what is left, so the unfinished part reads as a shape
        const x = c.i * PPM, y = c.j * PPM;
        const done = (i, j) => !isOn(i, j) || gs.grid[j * NX + i][key];
        if (done(c.i - 1, c.j)) view.fillRect(x, y, 3, PPM);
        if (done(c.i + 1, c.j)) view.fillRect(x + PPM - 3, y, 3, PPM);
        if (done(c.i, c.j - 1)) view.fillRect(x, y, PPM, 3);
        if (done(c.i, c.j + 1)) view.fillRect(x, y + PPM - 3, PPM, 3);
      }
    }
    // the edges: every metre done has the edger's smooth band along the board and the groove its
    // runner leaves; with an edging tool in hand, the metres still to do show along the board —
    // orange where it will take the trowel, blue where it's still too soft
    const edging = gs.tool === 'handTrowel' || gs.tool === 'trowelSmall', w = 0.09 * PPM, wo = 0.13 * PPM;
    for (const c of gs.cells) {
      const bs = boardsOf(c);
      if (!bs.length) continue;
      const x = c.i * PPM, y = c.j * PPM;
      for (const [di, dj] of bs) {
        const band = di > 0 ? [x + PPM - w, y, w, PPM] : di < 0 ? [x, y, w, PPM] : dj > 0 ? [x, y + PPM - w, PPM, w] : [x, y, PPM, w];
        if (c.edged & sideBit(di, dj)) {
          view.globalAlpha = 0.5; view.fillStyle = '#f4f4f1';
          view.fillRect(band[0], band[1], band[2], band[3]);
          view.globalAlpha = 0.5; view.fillStyle = '#76766f';
          if (di) view.fillRect(di > 0 ? x + PPM - w : x + w - 1.5, y, 1.5, PPM);
          else view.fillRect(x, dj > 0 ? y + PPM - w : y + w - 1.5, PPM, 1.5);
        } else if (edging && inn(c)) {
          const ok = cellH(c) >= 15;
          view.globalAlpha = ok ? 0.75 : 0.35; view.fillStyle = ok ? '#ff7a1a' : '#5aa9ff';
          if (di) view.fillRect(di > 0 ? x + PPM - wo : x, y, wo, PPM);
          else view.fillRect(x, dj > 0 ? y + PPM - wo : y, PPM, wo);
        }
      }
    }
    view.globalAlpha = 1;
    surfTex.needsUpdate = true;
    surfDirty = false;
  }

  // ------------------------------------------------------------------ HUD bits
  // Notes are small and few: two at most, under the HUD, gone once read. With voices on they're
  // heard rather than read — the player's own voice reads them out, unless somebody with a real
  // line is talking — and with subtitles off (the default) a note that was heard isn't put on the
  // screen at all. What couldn't be said out loud is always shown.
  const toastBox = $('#toasts');
  let subsOn = store('pourday.subs') === 'on';
  let readNotes = store('pourday.readNotes') !== 'off';
  function showNote(text, cls) {
    const d = document.createElement('div');
    d.className = 'toast' + (cls ? ' ' + cls : '');
    d.textContent = text;
    const life = clamp(2600 + text.length * 45, 3500, 8000);
    d.style.setProperty('--life', `${life}ms`);
    toastBox.prepend(d);
    while (toastBox.children.length > 2) toastBox.lastChild.remove();
    setTimeout(() => { d.classList.add('out'); setTimeout(() => d.remove(), 300); }, life);
    return d;
  }
  function toast(text, kind) {
    if (netCapture) { netCapture.push([text, kind]); return; }
    if (netRemote) return;
    const d = showNote(text, kind);
    const at = performance.now();
    if (kind === 'good' || kind === 'warn') sfx(kind === 'good' ? 'chime' : 'buzz');
    // Decided once the rest of this moment has run: a note is often followed straight away by its
    // owner saying the words, and then it has been heard already.
    queueMicrotask(() => {
      let heard = lastSayAt >= at - 40;
      if (!heard && readNotes && !/"/.test(text)) heard = say(firstPerson(text), 'me', 0);
      if (heard && !subsOn) d.remove();
      else if (!kind) sfx('pop');
    });
  }
  /** The player's own words, out loud: a curse, a groan, a thought. Shown only if it couldn't be heard. */
  function me(line, kind) {
    if (netRemote || netCapture || !line) return;
    const d = showNote(line.replace(/^"|"$/g, ''), 'me' + (kind ? ' ' + kind : ''));
    if (say(line, 'me', 1) && !subsOn) d.remove();
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
    if (isHost() && net.started && !spec.personal && gs.phase !== 'morning') netSend({ t: 'note', who: spec.who || '', title: spec.title || '', text: (spec.text || '').slice(0, 400), voice: typeof spec.voice === 'string' ? spec.voice : '', say: spec.say || '' });
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
    if (modalOpen || settingsOpen || gs.phase === 'title' || gs.phase === 'end' || gs.phase === 'morning') return 0;
    if (gs.waitMode === 'van') return 25;
    if (gs.waitMode === 'guard') return 8;
    if (gs.fastForward) return 14;
    return { prep: 0.6, pipes: 0.6, pour: 0.45, wash: 0.5, cure: 1.0 }[gs.phase] || 0;
  }
  function awayNow() {
    return gs.waitMode === 'van' || hyp(player.x, player.z, site.mid.x, site.mid.z) > 18;
  }
  function simulate(dm, away) {
    if (isGuest()) return;
    while (dm > 0) {
      const step = Math.min(dm, 2);
      gs.t += step;
      dm -= step;
      const landed = gs.loads.filter((l) => l && l.at !== null);
      if (landed.length) {
        loadsGoingOff();
        for (const l of landed) {
          if (l.h >= 100) continue;
          const b = l.h;
          l.h = Math.min(100, l.h + cureRate(gs.t) * stageMul(l.h, l.fast) * l.rate * step);
          if (Math.floor(b) !== Math.floor(l.h)) cellsDirty = true;
          // what the overlays show (too soft, ready) changes every few per cent
          if (Math.floor(b / 5) !== Math.floor(l.h / 5)) surfDirty = true;
        }
        if (gs.poured) {
          const before = gs.H;
          gs.H = Math.min(100, meanH());
          milestone(before, gs.H);
          loadMilestones();
        } else if (gs.phase === 'pour') pourMilestones();
      } else if (gs.poured && gs.H < 100) {
        const before = gs.H;
        gs.H = Math.min(100, gs.H + cureRate(gs.t) * stageMul(gs.H) * step);
        milestone(before, gs.H);
        if (Math.floor(before) !== Math.floor(gs.H)) cellsDirty = true;
      }
      if (gs.waitMode === 'van') gs.energy = clamp(gs.energy + 0.12 * step, 0, 100);
      else gs.energy = clamp(gs.energy - 0.04 * step, 0, 100);
      if (gs.truck && gs.truck.waiting) gs.truckWaitPaid += 1.5 * step;
      if (gs.phase !== 'morning') needsTick(step);
      if (gs.phase === 'wash' && gs.H >= 20) enterCure(true);
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
        if (spreadH()[1] >= 80) hardenMarks();
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
  /** Mid-pour: the first truck's end going off while the rest is still coming in. */
  function pourMilestones() {
    const first = firstLoad();
    if (!first) return;
    const note = (key, text) => { if (gs.milestones[key]) return; gs.milestones[key] = true; if (gs.waitMode) { gs.waitMode = null; showWait(); } toast(text, 'good'); };
    if (first.h >= 15) note('pe', `Truck ${first.no}'s concrete is at ${Math.floor(first.h)}%: its edges and corners will take the hand trowel now, pour or no pour.`);
    if (first.h >= 25) note('pp', `Truck ${first.no}'s end takes the pans now (${Math.floor(first.h)}%). Get a trowel on it between trucks, or it'll be past its best by the time the pour is done.`);
  }
  /** When the loads are far apart: the first part ready for the pans while the rest isn't. */
  function loadMilestones() {
    const [lo, hi] = spreadH();
    if (hi - lo < 5) return;
    const soft = gs.loads.filter((l) => l && l.at !== null).sort((a, b) => a.h - b.h)[0];
    const hard = gs.loads.filter((l) => l && l.at !== null).sort((a, b) => b.h - a.h)[0];
    if (!soft || !hard || soft === hard) return;
    const note = (key, text) => { if (gs.milestones[key]) return; gs.milestones[key] = true; if (gs.waitMode) { gs.waitMode = null; showWait(); } toast(text, 'good'); };
    if (hi >= 25 && lo < 25) note('p1', `Truck ${hard.no}'s concrete takes the pans now (${Math.floor(hard.h)}%). Truck ${soft.no}'s is still at ${Math.floor(soft.h)}% — the pans dig in there.`);
    if (hi >= 55 && lo < 55) note('b1', `Truck ${hard.no}'s end is ready for blades. Truck ${soft.no}'s isn't (${Math.floor(soft.h)}%).`);
    if (hi >= 85 && lo < 70) note('late1', `Truck ${hard.no}'s concrete is nearly set (${Math.floor(hard.h)}%). Whatever isn't closed there stays open.`);
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
          text: [fresh(L.mgrOpen), fresh(L.managerCall).replace(/\{n\}/g, n), fresh(L.mgrClose)].join(' '),
          choices: [
            { label: 'Hold the phone away from your ear', primary: true, fn: () => toast(fresh(L.managerAfter), 'warn') },
            { label: 'Blame the dog', fn: () => toast('"THE DOG IS NOT ON THE PAYROLL." He has a point. The dog would be cheaper.', 'warn') },
            { label: 'Call it a "textured finish"', fn: () => toast('A long silence. Then: "Send me the invoice for the textured finish. Addressed to yourself."', 'warn') },
            { label: 'Flip off the phone', fn: () => { gs.stats.flips++; toast('You flip off the phone. He can\'t see it. "I CAN HEAR YOU DOING THAT." He can\'t. Can he?', 'warn'); } },
          ],
        });
      } }],
    });
  }
  function hardenMarks() {
    gs.cells.forEach((c) => {
      if (c.marks.length && !c.defect && cellH(c) >= 80) { c.defect = true; surfDirty = true; }
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
    pts.push(farPoint(pick(sidesToday())));
    return pts;
  }
  const walkers = [];
  let walkerUid = 0;
  function spawnWalker(kind, path, speed, opts) {
    const g = kind === 'dog' ? '' : (opts && opts.who && opts.who.g) || (chance(0.5) ? 'f' : 'm');
    const kidG = g === 'kid' ? (chance(0.5) ? 'f' : 'm') : '';
    const m = kind === 'dog' ? makeDog(opts && opts.cat) : makePerson(Object.assign({ g: kidG || g }, chance(0.35) && !kidG ? { vest: pick([0xd4f53c, 0xff7a1a]) } : {}));
    if (g === 'kid') m.scale.setScalar(0.72);
    m.position.set(path[0].x, 0, path[0].z);
    m.traverse((o) => { if (o.isMesh) o.castShadow = true; });
    scene.add(m);
    sfx(opts && opts.cat ? 'meow' : kind === 'dog' ? 'bark' : 'voice', path[0].x, path[0].z);
    const w = Object.assign({ kind, m, path, seg: 0, speed, acc: 0, pause: 0, pose: null, heading: 0, shouted: 0, voice: personVoice(g, kidG), uid: ++walkerUid }, opts || {});
    w.voice.name = (opts && opts.who && opts.who.who) || (kidG ? 'A kid' : 'A passer-by');
    w.voice.at = () => w.m.position;
    walkers.push(w);
    return w;
  }
  function reroute(w, path, speed) {
    w.path = path;
    w.seg = 0;
    w.acc = 0;
    if (speed) w.speed = speed;
  }
  /**
   * The walker (or driver) under the crosshair, close enough to shout at or talk to. The view ray
   * against each body from its feet to the top of its head, not one point on its chest: aim at a
   * man's boots or a cat's ears and it's still him, or it. The nearest along the ray wins. [all]
   * counts somebody just shouted at too (shouting skips them for a few seconds; a finger doesn't).
   */
  function walkerInSight(all) {
    camera.getWorldDirection(tmpV);
    const o = camera.position;
    let best = null, bestT = 18;
    const check = (w, x, y0, y1, r, z) => {
      for (let k = 0; k <= 6; k++) {
        const y = y0 + ((y1 - y0) * k) / 6;
        const dx = x - o.x, dy = y - o.y, dz = z - o.z;
        const t = dx * tmpV.x + dy * tmpV.y + dz * tmpV.z;
        if (t < 0.3 || t >= bestT) continue;
        // a touch more forgiving further off, where a person is a few pixels across
        if (Math.hypot(dx - tmpV.x * t, dy - tmpV.y * t, dz - tmpV.z * t) < r + t * 0.012) { bestT = t; best = w; }
      }
    };
    const person = (w, x, y, z) => check(w, x, y + 0.1, y + 1.85, 0.3, z);
    const animal = (w, m, cat) => {
      const sz = (m.userData && m.userData.size) || (cat ? 0.55 : 1);
      check(w, m.position.x, m.position.y + 0.1, m.position.y + 0.8 * sz, 0.45 * sz, m.position.z);
    };
    walkers.forEach((w) => {
      if (!all && performance.now() <= w.shouted) return;
      if (w.kind === 'dog') animal(w, w.m, w.cat); else person(w, w.m.position.x, w.m.position.y, w.m.position.z);
    });
    if (helper.m) person({ kind: 'driver', who: 'helper', isHelper: true, voice: helper.voice }, helper.m.position.x, helper.m.position.y, helper.m.position.z);
    if (inTeam()) {
      net.crew.forEach((c) => { if (c.id !== net.me && c.m) person({ kind: 'driver', who: 'crew', crewId: c.id, name: c.name }, c.x, c.m.position.y, c.z); });
      net.remoteWalkers.forEach((w, uid) => {
        const t = { kind: w.kind === 'p' ? 'person' : 'dog', remoteWid: uid, m: w.m, cat: w.kind === 'c' };
        if (w.kind === 'p') person(t, w.m.position.x, w.m.position.y, w.m.position.z); else animal(t, w.m, w.kind === 'c');
      });
    }
    if (pumpGuy.visible) person({ kind: 'driver', who: 'pump' }, pumpGuy.position.x, pumpGuy.position.y, pumpGuy.position.z);
    if (mixGuy.visible) person({ kind: 'driver', who: 'mixer' }, mixGuy.position.x, mixGuy.position.y, mixGuy.position.z);
    return best;
  }
  /**
   * With nobody under the crosshair, the thing that is: the van, a truck, the loo, a tool, the
   * slab, the gravel, the sky — so the finger lands on what you actually pointed it at.
   */
  const aimRay = new THREE.Raycaster();
  function thingInSight() {
    camera.getWorldDirection(tmpV);
    const o = camera.position;
    const things = [[van, 'van'], [pump, 'pump'], [mixer, 'mixer'], [tripod, 'laser'], [loo, 'loo'], [kiosk, 'kiosk'], [ibc, 'tank'], [pile, 'pipes'], [jib, 'crane'], [hall, 'hall']];
    TOOL_IDS.forEach((id) => {
      const tl = gs.tools[id];
      if (!tl || tl.in !== 'ground') return;
      if (lying[id] && lying[id].visible) things.push([lying[id], 'tool:' + id]);
      if (machines[id] && machines[id].group.visible) things.push([machines[id].group, 'tool:' + id]);
    });
    const live = things.filter(([g]) => g && g.visible);
    aimRay.set(o, tmpV);
    aimRay.far = 60;
    aimRay.camera = camera;          // the name boards are sprites, and a sprite needs to know the camera
    let hits = [];
    try { hits = aimRay.intersectObjects(live.map(([g]) => g), true); } catch (e) { hits = []; }
    for (const h of hits) {
      if (h.distance > 60) break;
      let x = h.object;
      while (x) {
        const f = live.find(([g]) => g === x);
        if (f) return f[1];
        x = x.parent;
      }
    }
    // no thing: the ground, or what's above it
    if (tmpV.y < -0.02) {
      const t = (0.05 - o.y) / tmpV.y;
      return onSlab(o.x + tmpV.x * t, o.z + tmpV.z * t) ? 'slab' : 'gravel';
    }
    if (tmpV.y > 0.12) { const h = (gs.t % 1440) / 60; return h > 7 && h < 19 ? (chance(0.5) ? 'sun' : 'sky') : 'moon'; }
    return '';
  }
  function shoutAt(w) {
    if (w.crewId) { netSend({ t: 'poke', to: w.crewId, kind: 'shout' }); toast(`You shout at ${w.name}. ${fresh(L.crewShoutOut)}`); sfx('shout'); return; }
    if (w.remoteWid) { netSend({ t: 'shout', wid: w.remoteWid }); sfx('shout'); return; }
    if (w.kind === 'driver' && w.isHelper) { const l = fresh(L.helper.talk); toast(`${helper.name}: ${l}`); say(l, helper.voice); return; }
    if (w.kind === 'driver') { const line = pick(w.who === 'pump' ? L.driverTalk : L.mixTalk); toast(line); say(line, w.who === 'pump' ? 'pump' : 'truck'); return; }
    w.shouted = performance.now() + 5000;
    const p = P(w.m.position.x, w.m.position.z);
    if (w.kind === 'dog') {
      if (gs.sausage && !w.cat) {
        gs.sausage = false;
        toast(L.dogSausage, 'good');
        sfx('bark', p.x, p.z);
        reroute(w, [p, farPoint(pick(sidesToday()))], 6);
        w.state = 'leaving';
        remember('A dog tried for the slab. It left with your sausage instead.');
        return;
      }
      sfx('shout');
      if (w.cat) {
        if (chance(0.5)) { toast(L.cat.stays, 'warn'); w.pause = Math.max(w.pause, 8); return; }
        toast(L.cat.goes);
        w.pause = 0; w.state = 'leaving'; w.afterPause = null;
        reroute(w, [p, farPoint(pick(sidesToday()))], 2.2);
        return;
      }
      const leaves = chance(w.state === 'laps' ? 0.5 : 0.7);
      if (leaves) { toast(fresh(L.dogShoo), 'good'); reroute(w, [p, farPoint(pick(sidesToday()))], 5.5); w.state = 'leaving'; }
      else { toast(fresh(L.dogGame), 'warn'); if (w.state !== 'laps') { gs.stats.dogs++; remember('A dog did laps of the slab. You shouted. It loved it.'); } reroute(w, lapsFrom(p), 4.6); w.state = 'laps'; setTimeout(() => sfx('bark', w.m.position.x, w.m.position.z), 400); }
      w.pause = 0;
      return;
    }
    // a person: told off. Most go round; some hurry on across; some just stop and stare
    gs.stats.hell++;
    sfx('shout');
    setTimeout(() => sfx('voice', w.m.position.x, w.m.position.z), 500);
    const side = w.from || pick(sidesToday());
    const r = weighted([[0.5, 'back'], [0.3, 'hurry'], [0.2, 'freeze']]);
    if (r === 'back') { const l = fresh(L.shoutBack); toast(l, 'good'); say(l, w.voice); reroute(w, aroundFrom(onSlab(p.x, p.z) ? edgePoint(side) : p, side)); if (onSlab(p.x, p.z)) w.path.unshift(p); }
    else if (r === 'hurry') { const l = fresh(L.shoutHurry); toast(l, 'warn'); say(l, w.voice); w.speed *= 1.8; }
    else { toast(fresh(L.shoutFreeze), 'warn'); w.pause = 2.2; w.pose = 'look'; }
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
        { label: fresh(L.hellLabels), primary: true, fn: () => {
          gs.stats.hell++;
          if (chance(0.72)) { toast(who.back); say(who.back, w.voice); go(aroundFrom(here, w.from), 1.4); }
          else { const l = fresh(L.crossAnyway); toast(l, 'warn'); remember(`${who.who}: told where to go, crossed anyway.`); gs.stats.crossed++; go(acrossFrom(here, w.from), 1.5); }
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
    const side = pick(sidesToday()), pts = [farPoint(side), slabPoint(), farPoint(OPP[side])];
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
      stampLine('paw', farPoint(pick(sidesToday())), p, 0.3);
      stamp('curl', p.x, p.z, rnd(0, 6), true);
      toast(L.cat.away, 'warn');
      remember(L.cat.away);
      return;
    }
    const side = pick(sidesToday()), nap = slabPoint();
    spawnWalker('dog', wander([farPoint(side), edgePoint(side), nap], 0.8), 1.6, {
      cat: true, from: side, state: 'approach',
      onArrive: (w) => {
        w.state = 'napping';
        w.pause = rnd(25, 40);
        w.pose = 'sit';
        stamp('curl', w.m.position.x, w.m.position.z, rnd(0, 6), true);
        toast(L.cat.seen, 'warn');
        w.afterPause = (c) => { c.state = 'leaving'; reroute(c, [P(c.m.position.x, c.m.position.z), farPoint(pick(sidesToday()))], 1.8); };
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
    const from = farPoint(pick(sidesToday()));
    g.position.set(from.x, 7, from.z);
    scene.add(g);
    odd.push({ kind: 'drone', m: g, rotors, from, to: p, t: 0 });
  }
  function bagVisit(away) {
    const side = pick(sidesToday()), pts = [edgePoint(side), slabPoint(), edgePoint(OPP[side])];
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
            say(fresh(L.ufo.voice), 'alien');
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
          if (o.kind === 'ball') { toast(L.ball.seen, 'warn'); remember('A football bounced across the slab.'); say(L.ball.kid, kidVoice('ballKid')); } else { toast(L.bag.seen, 'warn'); remember('A plastic bag dragged a line across the slab.'); }
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
            say(L.drone.voice, kidVoice('droneKid'));
          }
        } else if (o.t > 30) { scene.remove(o.m); odd.splice(k, 1); }
      }
    }
  }

  function nuisance(away) {
    const mins = ((gs.t % 1440) + 1440) % 1440, dark = mins < 360 || mins > 1170;
    const kinds = [[0.4, 'cross'], [day.dogChance, 'dog'], [0.08, 'bird'], [0.1, 'foreman'], [0.07, 'kid'], [0.08, 'ball'], [0.07, 'cat'], [0.05, 'drone']];
    if (!gs.ufoDone && !day.indoor) kinds.push([dark ? 0.14 : 0.05, 'ufo']);
    if (day.wind >= 5 && !day.indoor) kinds.push([0.08, 'bag']);
    if (day.indoor) kinds.forEach((k) => { if (k[1] === 'drone') k[0] = 0; });
    if (day.rh > 76 && !gs.rained && !day.indoor) kinds.push([0.12, 'rain']);
    const kind = weighted(kinds);
    if (kind === 'cross') {
      const who = fresh(L.cross);
      if (away) {
        if (chance(0.65)) {
          const side = pick(sidesToday());
          const p = acrossFrom(farPoint(side), side);
          let n = 0;
          for (let k = 1; k < p.length; k++) n += stampLine('boot', p[k - 1], p[k], 0.72);
          if (n) { gs.stats.crossed++; const line = fresh(L.crossAway); toast(line, 'warn'); remember(line); }
        }
        return;
      }
      // they come over from somewhere and stop at the edge to ask
      const side = pick(sidesToday());
      spawnWalker('person', wander([farPoint(side), edgePoint(side)], 0.8), rnd(1.1, 1.5), { who, from: side, onArrive: askToCross });
    } else if (kind === 'dog') {
      if (away) {
        if (chance(0.85)) {
          gs.stats.dogs++;
          const pts = lapsFrom(farPoint(pick(sidesToday())));
          for (let k = 1; k < pts.length; k++) stampLine('paw', pts[k - 1], pts[k], 0.42);
          const line = fresh(L.dog.away);
          toast(line, 'warn');
          remember(line);
        }
        return;
      }
      // a dog heads for the slab, stops at the edge to size it up, then goes for it — unless
      // somebody looks it in the eye and shoos it, or has a sausage
      const side = pick(sidesToday());
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
            toast(fresh(L.dogGame), 'warn');
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
        who: 'Foreman, on the phone', title: 'Ring ring.', text: fresh(L.foreman), sound: 'ring', voice: 'foreman',
        choices: [
          { label: 'It\'s basically hard.', fn: () => toast('It is not basically hard. You both know it.') },
          { label: 'Tell the truth.', fn: () => toast('Foreman: "Concrete is just stubborn water." He hangs up.') },
          { label: 'Pretend the signal is bad.', primary: true, fn: () => toast('"Kkkhhh... you\'re... breaking... kkkhh." Flawless.', 'good') },
        ],
      });
    } else if (kind === 'kid') {
      if (away || chance(0.4)) {
        const side = pick(sidesToday());
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
    if (c && gs.phase === 'pour' && c.fill > 20) bootsGet(0.4);
    if (chance(0.25)) setTimeout(() => { if (dropPhone()) toast('Your phone went flying. It\'s on the ground somewhere near you.', 'warn'); }, 400);
    sfx('thud');
    if (c && (gs.phase === 'pour' || gs.H < 30)) sfx('splash');
    if (c && gs.phase === 'pour') { c.fill = Math.max(0, c.fill - 12); cellsDirty = true; }
    else if (c) stamp('butt', player.x, player.z, player.yaw, true);
    if (text) {
      toast(text, 'warn');
      remember(text);
      setTimeout(() => me(fresh(L.meOw)), 2200);
    } else {
      me(fresh(L.meFall), 'warn');
      remember(fresh(L.falls));
    }
  }
  // ------------------------------------------------------------------ the morning after
  // Some mornings you turn up still carrying last night: the world sways, your feet wander, and now
  // and then it all comes back up — on the gravel if you're lucky, in the fresh slab if you're not.
  // Coffee helps a bit. Time helps more.
  let lastNight = '';
  const retch = { t: 0, len: 2.8, spewed: false };
  let hangMutter = 0;
  function hangoverNow() {
    if (!gs || !gs.hangover) return 0;
    return clamp(1.1 - (gs.t - gs.hangStart) / 260, 0, 1);
  }
  function startRetch() {
    retch.t = retch.len;
    retch.spewed = false;
    hush();
    me(fresh(L.meVomit), 'warn');
  }
  const puddles = new THREE.Group();
  scene.add(puddles);
  const pukeMat = new THREE.MeshLambertMaterial({ color: 0x8f7a40, transparent: true, opacity: 0.85, depthWrite: false });
  function updateHangover(dt) {
    if (retch.t > 0) {
      retch.t -= dt;
      const into = retch.len - retch.t;
      const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
      if (into > 1.0 && into < 1.7) {
        // a stream, out and down, in front of you
        for (let k = 0; k < 6; k++) {
          emit(player.x + fx * 0.25, camera.position.y - 0.25, player.z + fz * 0.25,
            fx * rnd(1.2, 2.2) + rnd(-0.3, 0.3), rnd(-0.6, 0.4), fz * rnd(1.2, 2.2) + rnd(-0.3, 0.3),
            rnd(0.5, 0.9), pick([0x9a8446, 0x7d6a32, 0xb49a55]), rnd(0.03, 0.06));
        }
      }
      if (into > 1.0 && !retch.spewed) {
        retch.spewed = true;
        sfx('retch');
        const px = player.x + fx * 0.8, pz = player.z + fz * 0.8;
        gs.stats.puked = (gs.stats.puked || 0) + 1;
        if (stamp('puke', px, pz, rnd(0, 6), true)) {
          gs.stats.pukeSlab = (gs.stats.pukeSlab || 0) + 1;
          remember('You threw up in the fresh slab. It\'s in the concrete now. Forever. Like a time capsule nobody wanted.');
        } else if (!cellAt(px, pz) && puddles.children.length < 8) {
          const m = new THREE.Mesh(new THREE.CircleGeometry(rnd(0.25, 0.4), 12), pukeMat);
          m.rotation.x = -Math.PI / 2;
          m.position.set(px, rampY(px, pz) + 0.015, pz);
          m.scale.set(1, rnd(0.6, 1), 1);
          puddles.add(m);
        }
        if (chance(0.35)) setTimeout(() => toast(fresh(L.vomitNear)), 2600);
      }
      return;
    }
    const h = hangoverNow();
    if (h <= 0) return;
    if (h > 0.3 && gs.t >= gs.nextRetch && !gs.packing && gs.waitMode !== 'van' && player.fall <= 0 && !gs.fitting) {
      gs.nextRetch = gs.t + rnd(35, 95);
      startRetch();
      return;
    }
    const now = performance.now();
    if (now > hangMutter) {
      if (hangMutter) me(fresh(L.meHangover));
      hangMutter = now + rnd(55, 110) * 1000;
    }
  }

  // ------------------------------------------------------------------ bad luck
  // Small things going wrong, now and then, on top of the big ones: a gull, a wasp, a phone face
  // down in the concrete. Every day gets some; which ones is anybody's guess.
  let mishapAt = 0;
  function updateMishaps() {
    const now = performance.now();
    const live = ['prep', 'pipes', 'pour', 'wash', 'cure'].includes(gs.phase);
    if (!mishapAt) mishapAt = now + rnd(60, 120) * 1000;
    if (now < mishapAt || !live || gs.packing || gs.waitMode || gs.fastForward || retch.t > 0) return;
    if (now < duckUntil + 1500) { mishapAt = now + 5000; return; }
    mishapAt = now + rnd(75, 160) * 1000;
    const m = fresh(L.mishaps);
    toast(m.text, 'warn');
    remember(m.text);
    setTimeout(() => me(m.me), 2600);
    if (m.effect === 'energy') gs.energy = clamp(gs.energy - 8, 0, 100);
    else if (m.effect === 'energy+') gs.energy = clamp(gs.energy + 10, 0, 100);
    else if (m.effect === 'phone') dropPhone();
    else if (m.effect === 'trousers') charge('Trousers, split. The old ones are a flag now', 35);
    else if (m.effect === 'run') { shake = Math.max(shake, 0.5); gs.energy = clamp(gs.energy - 5, 0, 100); }
  }

  /** A toe under the mesh: not a fall, a stumble — and a word for the rebar. */
  let tripAt = 0;
  function trip() {
    if (player.fall > 0) return;
    tripAt = performance.now() + rnd(35, 70) * 1000;
    player.fall = 0.75;
    player.stumble = 0.75;
    sfx('clank', player.x, player.z);
    me(fresh(L.meTrip));
  }

  // ------------------------------------------------------------------ the day, phase by phase
  function startDay() {
    $('#title').hidden = true;
    $('#hud').hidden = false;
    restartAnim($('#hud'), 'intro');
    setTimeout(() => $('#hud').classList.remove('intro'), 1400);
    phoneReset();
    phoneObj.visible = false;
    phoneEl.classList.remove('cracked');
    // in case a day was left halfway through throwing the tools in the van
    $('#buttons').style.visibility = '';
    $('#btnFlip').style.visibility = '';
    $('#targetInfo').style.visibility = '';
    gs.phase = 'morning';
    const alarm = fresh(L.alarm);
    const driveTo = (snooze) => {
      const d = fresh(L.drive);
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
    if (lastNight) {
      gs.hangover = 1;
      gs.hangStart = gs.t;
      gs.nextRetch = gs.t + rnd(6, 22);
      hangMutter = performance.now() + 9000;
      remember(lastNight);
      setTimeout(() => toast(lastNight, 'warn'), 1800);
    }
    player.x = POS.vanDoor.x; player.z = POS.vanDoor.z;
    // a crew doesn't all stand in the same boots: co-workers turn up a little way along
    if (isGuest()) {
      const k = ((net.me.charCodeAt(0) || 0) + (net.me.charCodeAt(net.me.length - 1) || 0)) % 3 + 1;
      const q = vanPoint(-6.4, (k % 2 ? 1 : -1) * 1.7 * Math.ceil(k / 2) + 0.01);
      [player.x, player.z] = collide(q.x, q.z);
    }
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
      toast(fresh(L.arrive));
    }
    at(gs.pumpAt, pumpArrives);
    if (day.helper) at(gs.t + irnd(15, 40), sendHelper);
    if (day.pumpDelay >= 25 && gs.t < 7 * 60 - 5) {
      at(6 * 60 + 50, () => toast(fresh(L.pumpLate).replace('{eta}', clock(gs.pumpAt)), 'warn'));
    }
    buildPrepMarkers();
  }

  function buildPrepMarkers() {
    addMarker('unload', POS.vanDoor, 'Open the van', 2.2, () => !gs.prep.unload, () => {
      gs.prep.unload = true; gs.toolsUnloaded = true;
      sfx('door', POS.vanDoor.x, POS.vanDoor.z);
      vanBack.want = 1;
      // whoever opened it unpacks it; the others hear where everything landed
      if (netRemote) return true;
      vanBack.onOpen = () => {
        unloadTools();
        sfx('clank', POS.vanDoor.x, POS.vanDoor.z);
        // some of it was only waiting for the door
        const out = ['hammer', 'pliers', 'cutter', 'handTrowel', 'float', 'shovel'].filter((id) => lying[id]).sort(() => Math.random() - 0.5).slice(0, irnd(1, 3));
        out.forEach((id, k) => {
          const a = vanPoint(-2.5, rnd(-0.5, 0.5)), b = vanPoint(-rnd(6.8, 9), rnd(-2.6, 2.6));
          setTimeout(() => launchTool(id, new THREE.Vector3(a.x, 1.2, a.z), b, rnd(0.6, 0.9)), 250 + k * 380);
        });
        const names = out.map((id) => TOOLS[id].the);
        toast(fresh(L.vanSpill).replace('{t}', names.length > 1 ? names.slice(0, -1).join(', ') + ' and ' + names[names.length - 1] : names[0]) + ' The rest is on the ramp; the machines are at the bottom of it.' + (day.area > 50 ? ' The ride-on came too.' : ''), 'warn');
      };
    });
    addMarker('laserFetch', POS.vanSide, 'Take the laser', 1.2, () => gs.prep.unload && gs.laserInVan && !gs.laserSetup && !gs.carrying && vanBack.open >= 1, () => {
      gs.laserInVan = false;
      if (!netRemote) gs.carrying = 'laser';
      sfx('clank', POS.vanSide.x, POS.vanSide.z);
      toast('Laser case out of the van. Carry it to the spot by the slab and set it up.');
    }, { tool: 'hands' });
    site.forms.forEach((f, k) => {
      addMarker('form' + k, f, 'Check formwork', 1.8, () => !gs.prep.form[k] && !gs.pourStarted, () => {
        gs.prep.form[k] = true;
        toast(pick(f.run === site.weak ? L.formWeak : L.formOk), f.run === site.weak ? 'good' : '');
        if (!netRemote && chance(0.18)) setTimeout(() => breakHandTool('hammer', fresh(L.hammerBreaks), 18), 1200);
      }, { tool: 'hammer' });
    });
    // The laser isn't set and go: the tripod goes up, the head is levelled until the bubble sits,
    // a height is taken off the benchmark, and the boards are checked against it at the corners.
    addMarker('laser', POS.tripod, 'Set up the tripod', 2.2, () => !gs.laserSetup && gs.carrying === 'laser', () => {
      if (!netRemote) gs.carrying = null;
      gs.laserSetup = 1;
      tripod.visible = true;
      sfx('clank', POS.tripod.x, POS.tripod.z);
      toast('Tripod up, legs stamped in. Now level the head: the bubble has to sit in the middle.');
      return true;
    }, { tool: 'hands' });
    // levelled from the far side, away from the slab: the formwork check sits on the near side
    addMarker('laserLevel', P(POS.tripod.x - 0.5, POS.tripod.z), 'Level the laser', 999, () => gs.laserSetup === 1, () => {
      gs.laserSetup = 2;
      sfx('beep', POS.tripod.x, POS.tripod.z);
      toast('Level. The head spins up and beeps: it has found itself. Now a height off the benchmark — the painted peg with the nail.', 'good');
      return true;
    }, { tool: 'hands' });
    addMarker('laserBench', site.bench, 'Shoot the benchmark', 2.4, () => gs.laserSetup === 2, () => {
      gs.laserSetup = 3;
      gs.benchRead = (1.25 + ((site.bench.x * 7.3 + site.bench.z * 3.1) % 1 + 1) % 1 * 0.4).toFixed(3);
      sfx('grade');
      toast(`Benchmark: the staff reads ${gs.benchRead} m. The slab's top is ${day.thick} mm over the base — the receiver's set to that. Now the boards, corner by corner.`, 'good');
      return true;
    }, { tool: 'hands', w: 2.4 });
    site.levelChecks.forEach((c, k) => {
      addMarker('laserCheck' + k, c, 'Check the height', 1.8, () => gs.laserSetup === 3 && !c.done, () => {
        c.done = true;
        sfx('grade', c.x, c.z);
        const o = c.off, mm = `${o > 0 ? '+' : ''}${o} mm`;
        if (Math.abs(o) <= 4) toast(`The ${c.name}: ${mm}. Near enough.`);
        else { toast(`The ${c.name}: ${mm} — ${o < 0 ? 'low' : 'high'}. That board needs knocking ${o < 0 ? 'up' : 'down'} before the pour.`, 'warn'); if (!netRemote) me(pick(['"Who set that board? Oh. Me."', '"A centimetre out. Of course it is."', '"And that\'s why we check."'])); }
        laserMaybeReady();
        return true;
      }, { tool: 'hands', w: 2.2 });
      addMarker('boardFix' + k, P(c.x + 0.25, c.z + 0.25), `Knock the board ${c.off < 0 ? 'up' : 'down'}`, 2.4, () => c.done && Math.abs(c.off) > 4 && !c.fixed && !gs.pourStarted, () => {
        c.fixed = true;
        sfx('hammer', c.x, c.z);
        toast(`Stake knocked, board ${c.off < 0 ? 'up' : 'down'}, shot again: on height. ${pick(['Beautiful.', 'The laser is pleased. Nobody else is.', 'That\'s a centimetre of slab you won\'t get called about.'])}`, 'good');
        return true;
      }, { tool: 'hammer', w: 2.4 });
    });
    site.ties.forEach((t, k) => {
      addMarker('tie' + k, t, 'Tie the mesh', 1.6, () => !t.done && !gs.pourStarted, () => {
        t.done = true;
        t.bar.visible = false;
        t.twist.visible = true;
        const left = site.ties.filter((x) => !x.done).length;
        toast(fresh(L.tie) + (left ? ` ${left} to go.` : ' That\'s the mesh tied.'));
      }, { tool: 'pliers', w: 2.2 });
    });
    site.cuts.forEach((t, k) => {
      addMarker('cut' + k, t, 'Cut the bar', 1.2, () => !t.done && !gs.pourStarted, () => {
        t.done = true;
        t.bar.scale.y = 0.08;
        t.bar.position.y = rebarY() + 0.03;
        sfx('snip', t.x, t.z);
        toast(fresh(L.cut));
        if (!netRemote && chance(0.12)) setTimeout(() => breakHandTool('cutter', fresh(L.cutterBreaks), 35), 1200);
      }, { tool: 'cutter', w: 2.2 });
    });
  }

  function driveIn(group, target, seconds, done) {
    group.visible = true;
    drives.push({ group, from: group.position.x, to: target, t: 0, seconds, done: () => {
      sfx('brake', group.position.x, group.position.z);
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
      modal({ who: 'Pump driver', title: 'The boom pump is here.', voice: 'pump', text: fresh(L.pumpArrive) + '\n\nNo pipes to carry today. He puts the legs down and swings the boom over the slab; the hose hangs off the end and he follows you with it on his remote. Mostly.', choices: [{ label: 'Lovely', primary: true }] });
      at(gs.t + 12, () => {
        gs.pipes = PIPE_N;
        gs.tools.hose = { in: 'ground', x: SLAB.x1 - 1.5, z: gz(ENTRY.j) + 0.5, yaw: Math.PI / 2 };
        toast('Boom up, hose hanging over the slab. Now we wait for the mixer.', 'good');
        maybeStartPour();
      });
    } else {
      pile.visible = true;
      modal({ who: 'Pump driver', title: 'The pump is here.', voice: 'pump', text: fresh(L.pumpArrive) + '\n\nLay the pipe line from the pump to the slab: grab a pipe from the pile, carry it to the next marker, clamp it.', choices: [{ label: 'On it', primary: true }] });
      buildPipeMarkers();
    }
    const first = Math.max(7 * 60 + 30 + day.truckDelays[0], gs.t + 25);
    scheduleTruck(first);
    if (first - (7 * 60 + 30) >= 20) at(Math.max(gs.t + 5, 7 * 60 + 25), () => toast(fresh(L.truckLate).replace('{eta}', clock(first)), 'warn'));
  }

  function buildPipeMarkers() {
    addMarker('pile', POS.pile, 'Grab a pipe', 1.2, () => gs.pipes < PIPE_N && !gs.carrying, () => {
      gs.carrying = 'pipe';
      sfx('clank', POS.pile.x, POS.pile.z);
      toast(gs.pipes === 0 ? 'A pipe: 3 m of steel and regret. Carry it to the first marker by the pump.' : 'Another one. Your shoulder remembers the last.');
    }, { tool: 'hands' });
    PIPE_ROUTE.forEach((p, k) => {
      addMarker('pipe' + k, p, `Pipe ${k + 1} of ${PIPE_N}`, 1.6, () => gs.carrying === 'pipe' && gs.pipes === k, () => {
        gs.carrying = null;
        gs.pipes++;
        sfx('clank', p.x, p.z);
        pipeMeshes.push(pipeBetween(k === 0 ? POS.pumpOut : PIPE_ROUTE[k - 1], p));
        toast(fresh(L.pipe));
        gs.energy = clamp(gs.energy - 2, 0, 100);
        if (gs.pipes === PIPE_N) {
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
    if (when - gs.t >= 30 && no > 1) toast(fresh(L.truckLate).replace('{eta}', clock(when)), 'warn');
  }

  function truckArrives(no) {
    gs.truckNo = no;
    gs.truck = { no, left: loadOf(no), waiting: true };
    if (!gs.loads[no]) {
      const kind = no === 1 ? weighted([[0.7, 'standard'], [0.12, 'retarded'], [0.1, 'hot'], [0.08, 'accelerated']])
        : weighted([[0.45, 'standard'], [0.17, 'retarded'], [0.14, 'accelerated'], [0.14, 'hot'], [0.1, 'wet']]);
      gs.loads[no] = { no, kind, rate: MIXES[kind].rate * rnd(0.94, 1.06), h: 0, at: null };
      if (no > 1 && kind !== 'standard') setTimeout(() => toast(`Truck ${no}'s ticket: ${MIXES[kind].say}`, 'warn'), 2500);
    }
    gs.fastForward = null;
    mixer.position.set(70, 0, day.boom ? BOOM_AT.z : POS.mixer.z);
    // backwards, the chute first: it empties into the pump's hopper, at the back of the pump
    mixer.rotation.y = Math.PI;
    driveIn(mixer, day.boom ? BOOM_AT.x + 8.3 : POS.mixer.x, 5);
    if (no === 1) {
      // a load that is already a problem doesn't also get to be stiff or soupy
      if (day.trouble) day.mix = 'ok';
      gs.mixState = day.mix;
      gs.mixFactor = day.mix === 'soup' ? 0.75 : day.mix === 'stiff' ? 1.08 : 1;
    }
    toast(`Truck ${no} is here with ${gs.truck.left.toFixed(1)} m³.` + (gs.pipes < PIPE_N ? ' The line isn\'t laid. The driver starts a waiting-time clock at 90 €/h.' : ''), gs.pipes < PIPE_N ? 'warn' : '');
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
        who: 'Mixer driver', title: 'The drum is empty.', text: fresh(L.emptyTruck) + '\n\nThe plant can send another one.', voice: 'truck',
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
    if (!gs.truck || gs.pipes < PIPE_N) return;
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
      text: fresh(L.truckDriver) + '\n\n' + mixText + `\n\n${day.boom ? 'Pick up the hose hanging off the boom, over the east edge — the boom follows you' : 'Pick up the hose at the end of the line, on the east edge'} — and look at where it goes. Float it to the laser with the float from the tarp. Aim for ${day.thick} mm everywhere. Don't pour more than you need: ${orderedM3().toFixed(1)} m³ ordered, in ${trucksOrdered()} ${trucksOrdered() > 1 ? 'trucks' : 'truck'}.`,
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
    boardsOut();
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
    at(gs.t + 6, () => { toast(fresh(L.wash)); });
    const rep = slabReport(), v = { d: Math.abs(rep.off).toFixed(0), t: day.thick, r: rep.sd.toFixed(1), h: dur(gs.pourMins) };
    const moan = [];
    if (rep.off < -4) moan.push(fillIn(fresh(L.badPour.thin), v));
    if (rep.off > 5) moan.push(fillIn(fresh(L.badPour.thick), v));
    if (rep.sd > 5) moan.push(fillIn(fresh(L.badPour.bumpy), v));
    if (rep.slowBy > 10) moan.push(fillIn(fresh(L.badPour.slow), v));
    if (moan.length) {
      at(gs.t + 4, () => {
        remember('The foreman called about the pour. Words like "pay" and "cut" were used.');
        modal({
          who: 'Phone · the foreman', title: moan.length > 1 ? 'He has a list.' : 'He has a point.', sound: 'ring', voice: 'foreman',
          text: moan.join('\n\n') + '\n\n' + fresh(L.badPour.close),
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
    addMarker('wash', POS.ibcFront, 'Wash tools and boots', 2.4, () => gs.phase === 'wash' || (held() && dirtOf(held()) > 0.05) || (!held() && (gs.boots || 0) > (gs.bootsFloor || 0) + 0.2) || (!held() && !!gs.accident && gs.accident.kind === 'shit' && !gs.accident.washed), () => {
      // a co-worker's washing: their tool comes clean on its own; the host sees if that's the lot
      if (netRemote) { if (gs.phase === 'wash' && !isGuest() && !dirtyTools().filter((t) => !isMachine(t)).length) enterCure(); return true; }
      const id = held();
      if (!id && gs.accident && gs.accident.kind === 'shit' && !gs.accident.washed) {
        // behind the tank, with the hose, in the cold: the worst five minutes of the day, and the flies leave
        gs.accident.washed = true;
        sfx('splash', POS.ibc.x, POS.ibc.z);
        simulate(5, awayNow());
        toast('You hose yourself down behind the water tank. Cold water, in front of the road. The tank will never be the same. Now the spare trousers, in the van.', 'warn');
        if (gs.phase !== 'wash') return true;
      } else if (!id && (gs.boots || 0) > (gs.bootsFloor || 0) + 0.05) {
        const set = bootsSet();
        // what had set stays on them for the rest of the day, wash them as often as you like
        if (set) gs.bootsFloor = 0.12;
        gs.boots = gs.bootsFloor || 0;
        gs.bootsAt = gs.t;
        toast(set ? 'You hose your boots. What had set on them stays on them. Heavier boots, for ever.'
          : gs.bootsFloor ? 'You hose off the fresh stuff. The grey that set on them this morning stays.' : fresh(L.bootsWashed), set ? 'warn' : 'good');
        if (gs.phase !== 'wash') return true;
      } else if (id && dirtOf(id) > 0.05) {
        const set = dirtSet(id);
        gs.dirt[id] = { d: 0, at: gs.t };
        toast(set ? `You chip and scrub at the ${TOOLS[id].name.toLowerCase()}. Most of it comes off. The rest is part of it now.` : `${TOOLS[id].name} washed. ${fresh(L.washed)}`, set ? 'warn' : 'good');
        if (set) charge(`Set concrete chipped off the ${TOOLS[id].name.toLowerCase()}`, isMachine(id) ? 60 : 20);
      } else if (gs.phase !== 'wash') { toast('Nothing in your hands to wash. Bring the dirty one here.'); return false; }
      if (gs.phase === 'wash') {
        const still = dirtyTools().filter((t) => !isMachine(t));
        if (still.length) { toast(`Still covered: ${theList(still)}. Bring ${still.length > 1 ? 'them' : 'it'} here too.`); return false; }
        enterCure();
      }
      return true;
    });
    addMarker('loo', POS.looFront, 'The toilet', 1.6, () => gs.phase !== 'end' && gs.phase !== 'morning', () => useLoo(), { tool: 'hands', w: 1.8 });
    const live = () => ['prep', 'pipes', 'pour', 'wash', 'cure'].includes(gs.phase) && !gs.packing;
    phoneMarker = addMarker('phone', P(0, 0), 'Your phone', 0.6, () => !!gs.phoneDown && phoneFall.t >= 1, () => pickUpPhone(), { w: 1.8 });
    addMarker('spares', POS.vanSeat, 'Spare clothes', 2.2, () => live() && (!!gs.accident || (!gs.spareBoots && (gs.boots || 0) > 0.25)), () => changeClothes(), { tool: 'hands', w: 2.2 });
    addMarker('behindVan', POS.vanCorner, 'Behind the van', 2.5, () => live() && !gs.accident && gs.needs.wee > 60, () => pissBehindVan(), { tool: 'hands', w: 2.2 });
    buildLateMarkers2();
  }
  /** After the washing up, or when the slab won't wait for it: the long wait begins. */
  function enterCure(rushed) {
    if (gs.phase !== 'wash') return;
    gs.washed = true;
    gs.phase = 'cure';
    const dirty = dirtyTools().filter((t) => !isMachine(t));
    if (rushed && dirty.length) toast(`No time to wash everything: the slab is going off. ${theList(dirty)} ${dirty.length > 1 ? 'are' : 'is'} still covered, and it's setting.`, 'warn');
    else toast('Tools washed. You washed your boots too, then stepped in the slurry. Classic.');
    {
      modal({
        personal: true,
        who: 'Now it hardens', title: 'The waiting part.',
        text: `It's ${Math.round(tempAt(gs.t))} °C with ${day.rh}% humidity and a ${day.thick} mm slab of ${day.area} m². Keep an eye on the hardness meter.\n\n` +
          `• Pans from about 25%: take a power trowel from the bottom of the van\'s ramp — they come with pans on. One to three passes.${day.area > 50 ? ' The ride-on does a big slab in half the time.' : ''}\n• Blades from about 55%: fit them on the machine (button next to Put down), then pass again.\n• The edges all the way round, from 15%: run the small edge trowel along the boards, or the hand trowel. Corners and pipe collars with the hand trowel.${edgeLeft() < edgeMetres()[1] ? ` ${edgeMetres()[0]} m of ${edgeMetres()[1]} already done.` : ''}\n• Pack up the laser and put it in the van.\n• Nobody leaves before 95%.\n\n` +
          'Meanwhile: guard the slab, nap in the van, or walk to the kebab stand.\n\nFootprints: the float takes them out under 50%, the hand trowel under 70%, the machines up to about 80%. After that they\'re in it for good.\n\nWhen you go home, every tool goes back in the van, washed.',
        choices: [{ label: 'Right', primary: true }],
      });
    }
  }
  function buildLateMarkers2() {
    addMarker('batteries', POS.vanDoor, 'Get batteries', 1.4, () => !gs.laserBattery && !gs.pourDone, () => {
      gs.laserBattery = true;
      toast('Fresh batteries. The laser beeps like nothing happened. You know what happened.', 'good');
    });
    // the laser goes back in its case, the receiver with it: nothing left to beep at
    addMarker('laserPack', POS.tripod, 'Pack up the laser', 2.4, () => gs.pourDone && (gs.prep.laser || gs.laserSetup) && !gs.laserPacked && gs.carrying !== 'laser' && tripod.visible, () => {
      tripod.visible = false;
      if (!netRemote) gs.carrying = 'laser';
      gs.laserOn = false;
      sfx('clank', POS.tripod.x, POS.tripod.z);
      toast('Laser off, tripod folded. To the van with it, receiver and all.');
    }, { tool: 'hands' });
    addMarker('laserVan', POS.vanSide, 'Laser in the van', 1.2, () => gs.carrying === 'laser' && (gs.prep.laser || gs.laserSetup), () => {
      if (!netRemote) gs.carrying = null;
      gs.laserPacked = true;
      gs.laserInVan = true;
      sfx('door', POS.vanDoor.x, POS.vanDoor.z);
      toast('Laser in its case, the receiver in the glovebox. No more beeping today.', 'good');
    });
    addMarker('lunch', POS.kioskFront, 'Lunch', 1.2, () => gs.phase === 'cure' && gs.H < 90, () => { lunch(); });
    addMarker('home', POS.vanDoor, 'Go home', 1.2, () => !isGuest() && !gs.packing && ((gs.phase === 'cure' && (gs.panPasses.length > 0 || gs.gaveUp) && (gs.carrying !== 'laser' || gs.gaveUp)) || (gs.phase === 'wash' && gs.gaveUp)), () => { tryGoHome(); });
    site.edges.forEach((e, k) => {
      // corners and collars: the hand trowel only, as soon as the concrete there will take it —
      // during the pour too, where the first truck's end has gone off
      addMarker('edge' + k, e, e.label, 2.2, () => !e.done && concreteIn(e.x, e.z) && hAt(e.x, e.z) >= 10, () => {
        if (hAt(e.x, e.z) < 15) { toastOnce('edgesoft', 'Too soft. You\'re drawing in it, not troweling it. Give it a bit.', 'warn', 20000); return false; }
        e.done = true;
        gs.edgesDone++;
        if (!netRemote) { addDirt(gs.tool, 0.12); troweledOut(e.x, e.z, player.x, player.z); }
        const left = site.edges.length - gs.edgesDone;
        if (hAt(e.x, e.z) > 85) { gs.edgeNotes.push('late'); toast(`${e.label}: too hard to close properly. It'll do. It won't be pretty.`, 'warn'); }
        else toastOnce('edge', `${e.label} done. ${left ? `${left} more corner${left > 1 ? 's' : ''} and collar${left > 1 ? 's' : ''} to go.` : 'That\'s all the corners.'}`, 'good', 8000);
        return true;
      }, { w: 2.2, tool: 'handTrowel' });
    });
  }

  function lunch() {
    if (isGuest()) toastOnce('lunchcrew', `The clock is ${net.hostName}'s: your lunch doesn't stop it.`, '', 600000);
    modal({
      personal: true,
      who: 'Kebab & Coffee', title: 'What\'ll it be?', text: L.lunch,
      choices: [
        { label: 'Kebab, extra garlic (40 min)', primary: true, fn: () => { gs.energy = clamp(gs.energy + 45, 0, 100); gs.needs.poo += 45; gs.needs.wee += 10; simulate(40, true); toast('Kebab. The garlic will guard the slab for you for the rest of the day.', 'good'); remember('Kebab with extra garlic.'); } },
        { label: 'Sausage in a bun, one to go (30 min)', fn: () => { gs.energy = clamp(gs.energy + 32, 0, 100); gs.needs.poo += 25; gs.sausage = true; simulate(30, true); toast('You keep one sausage "for later". Later has plans for it.', 'good'); } },
        { label: 'Coffee and a thermos refill (10 min)', fn: () => { gs.energy = clamp(gs.energy + 12, 0, 100); gs.needs.wee += 20; gs.cups = 3; simulate(10, true); toast('Thermos full. You are, again, a person.', 'good'); } },
        { label: 'Nothing. Back to the slab.' },
      ],
    });
  }

  // ------------------------------------------------------------------ the toilet, and what you need it for
  // A piss comes round every few hours, faster with coffee; a shit after lunch, with the kebab's
  // compliments. Hold on too long and you'll know about it before it happens — hopping from foot to
  // foot, cramps that fold you in half — and then it happens, and you're cleaning up.
  function needsTick(step) {
    const n = gs.needs;
    n.wee += 0.2 * step;
    n.poo += 0.07 * step;
    const warn = (key, text, kind) => { if (n.warned[key]) return; n.warned[key] = true; if (gs.waitMode) { gs.waitMode = null; showWait(); } toast(text, kind); say(text, 'me'); };
    if (n.wee > 70) warn('wee70', fresh(L.weeSoon), 'warn');
    if (n.wee > 90) warn('wee90', 'You really need a piss. The toilet is the blue box. ' + fresh(L.weeSoon), 'warn');
    if (n.poo > 70) warn('poo70', fresh(L.pooSoon), 'warn');
    if (n.poo > 90) warn('poo90', 'You need a shit, and soon. The toilet, now. ' + fresh(L.pooSoon), 'warn');
    if (n.wee >= 100) accident('piss');
    if (n.poo >= 100) accident('shit');
  }
  /** It happened. A piss down one leg, or worse: the trousers go, and you have to clean up. */
  function accident(kind) {
    const n = gs.needs;
    if (kind === 'piss') { n.wee = 5; n.warned.wee70 = n.warned.wee90 = false; } else { n.poo = 5; n.warned.poo70 = n.warned.poo90 = false; n.wee = Math.min(n.wee, 25); }
    if (gs.waitMode) { gs.waitMode = null; showWait(); }
    const kindNow = (gs.accident && gs.accident.kind === 'shit') || kind === 'shit' ? 'shit' : 'piss';
    gs.accident = { kind: kindNow, washed: false, at: gs.t };
    gs.stats.accidents = (gs.stats.accidents || 0) + 1;
    gs.energy = clamp(gs.energy - (kind === 'shit' ? 15 : 6), 0, 100);
    if (kind === 'piss' && !inVanNow()) puddleAt(player.x, player.z);
    if (inVanNow()) { gs.vanSeat = true; charge('The van seat, valeted', 45); }
    toast(fresh(kind === 'shit' ? L.shatSelf : L.pissedSelf), 'warn');
    setTimeout(() => me(fresh(kind === 'shit' ? L.meShat : L.mePissed), 'warn'), 2600);
    remember(kind === 'shit' ? 'You shat yourself on site. The less said the better.' : 'You pissed yourself on site. It was a long pour.');
    setTimeout(() => toast(kind === 'shit' ? 'Clean up: hose yourself down at the water tank, then the spare trousers in the van.' : 'Clean up: the spare trousers are in the van.', 'warn'), 5200);
  }
  const puddleMat = new THREE.MeshLambertMaterial({ color: 0x4d4330, transparent: true, opacity: 0.55, depthWrite: false });
  function puddleAt(x, z) {
    const p = mesh(new THREE.CircleGeometry(0.34, 14), puddleMat, x, groundY(x, z) + 0.014, z);
    p.rotation.x = -Math.PI / 2;
    p.scale.set(1, rnd(0.6, 0.9), 1);
    puddles.add(p);
  }
  // holding it in, and what everybody else makes of you afterwards
  const cramp = { t: 0, len: 1.3 };
  let crampAt = 0, gurgleAt = 0, holdAt = 0, reactAt = 0;
  const flies = [0, 1, 2].map(() => { const f = box(0.012, 0.006, 0.014, 0x111111, 0, 0, 0); f.visible = false; return f; });
  function urgePiss() { return gs.accident ? 0 : clamp((gs.needs.wee - 75) / 25, 0, 1); }
  function updateNeedsAct(dt) {
    const now = performance.now(), n = gs.needs;
    cramp.t = Math.max(0, cramp.t - dt);
    const live = ['prep', 'pipes', 'pour', 'wash', 'cure'].includes(gs.phase) && !gs.packing && !inVanNow();
    if (live && n.poo > 80 && !gs.accident && now > crampAt) {
      crampAt = now + rnd(22, 40) * 1000;
      cramp.t = cramp.len;
      sfx('gurgle');
      me(fresh(L.cramp));
    } else if (live && n.poo > 62 && now > gurgleAt) { gurgleAt = now + rnd(15, 30) * 1000; sfx('gurgle'); }
    if (live && urgePiss() > 0.3 && now > holdAt) {
      holdAt = now + rnd(30, 50) * 1000;
      me(fresh(L.pissHold));
    }
    if (live && urgePiss() > 0 && (streamOn || markerAnim() === 'wash')) toastOnce('runningWater', 'Running water. Why does it always have to be running water.', '', 90000);
    // the flies, when it's the worse of the two, round your head and through your view
    const a = gs.accident, buzzing = !!(a && a.kind === 'shit' && !a.washed && gs.phase !== 'end');
    flies.forEach((f, k) => {
      f.visible = buzzing;
      if (!buzzing) return;
      const t = now / 1000 * (1.3 + k * 0.4) + k * 2.1;
      f.position.set(camera.position.x + Math.sin(t * 1.7) * (0.35 + k * 0.12), camera.position.y - 0.15 + Math.sin(t * 2.3) * 0.25, camera.position.z + Math.cos(t * 1.3) * (0.35 + k * 0.1));
      f.rotation.y = t * 3;
    });
    // whoever's near gets a whiff, or a look at your trousers
    if (a && live && now > reactAt) {
      reactAt = now + rnd(18, 32) * 1000;
      const near2 = [];
      walkers.forEach((w) => { if (w.kind !== 'dog' && hyp(w.m.position.x, w.m.position.z, player.x, player.z) < 7) near2.push([w.m, w.voice, 'Somebody']); });
      if (pumpGuy.visible && hyp(pumpGuy.position.x, pumpGuy.position.z, player.x, player.z) < 8) near2.push([pumpGuy, 'pump', 'Pump driver']);
      if (mixGuy.visible && hyp(mixGuy.position.x, mixGuy.position.z, player.x, player.z) < 8) near2.push([mixGuy, 'truck', 'Truck driver']);
      if (helper.m && hyp(helper.m.position.x, helper.m.position.z, player.x, player.z) < 8) near2.push([helper.m, helper.voice, helper.name]);
      if (near2.length) {
        const [m, v, who] = pick(near2), l = fresh(a.kind === 'shit' && !a.washed ? L.smellReact : L.wetReact);
        toast(`${who}: ${l}`, 'warn');
        say(l, v);
        gesture(m, a.kind === 'shit' ? 'clutch' : 'shout', 1.6, m === pumpGuy);
      }
    }
  }

  // Who is in the toilet: nobody, or somebody, and then for a while. The window by the knob says
  // which — red, somebody's in; green, go on — and pulling the knob while it's red gets you an
  // answer from inside, a different one every time. He comes out when he's done, and walks off.
  const LOO_WHO = {
    pump: { name: 'The pump driver', voice: 'pump', look: { shirt: 0x2f3540, vest: 0xff7a1a, hat: 'hard', hatColor: 0xf2f0ea }, mins: [8, 15] },
    truck: { name: 'The truck driver', voice: 'truck', look: { shirt: 0x3b5b8c, vest: 0xd4f53c, hat: 'cap' }, mins: [4, 8] },
    kebab: { name: 'The kebab man', voice: { p: 0.92, r: 1.02, key: 'kebab', g: 'm' }, look: { shirt: 0xf2f0ea, hat: 'hair' }, mins: [4, 9] },
    office: { name: 'The site engineer', voice: { p: 1.04, r: 1.06, key: 'office', g: 'm' }, look: { shirt: 0x5a7fa8, vest: 0xd4f53c, hat: 'hard', hatColor: 0x2c6ad6 }, mins: [3, 7] },
    stranger: { name: 'Somebody', voice: { p: 0.9, r: 0.98, key: 'looStranger', g: 'm' }, look: {}, mins: [3, 6] },
  };
  function looVoice(who) {
    const v = LOO_WHO[who].voice;
    return typeof v === 'string' ? v : Object.assign({}, v, { name: LOO_WHO[who].name, at: () => loo.position });
  }
  /** The pump driver goes when the pump isn't pumping, the truck driver while his truck waits. */
  function looCandidates() {
    const out = [[2, 'kebab'], [1.5, 'office'], [1, 'stranger']];
    if (isGuest()) return out;
    const pumping = gs.phase === 'pour' && gs.truck && !gs.truck.waiting;
    if (pumpGuy.visible && gs.pumpHere && !pumping && !(gs.tools.hose && gs.tools.hose.in === 'pumpman')) out.push([3, 'pump']);
    if (mixGuy.visible && gs.truck && gs.truck.waiting) out.push([1, 'truck']);
    return out;
  }
  let leaver = null;
  function updateLoo(dt) {
    const st = gs.loo || (gs.loo = { occ: null, next: gs.t + rnd(20, 60) });
    const live = ['prep', 'pipes', 'pour', 'wash', 'cure'].includes(gs.phase);
    if (st.occ) {
      if (st.occ.who === 'pump') pumpGuy.visible = false;
      if (st.occ.who === 'truck') mixGuy.visible = false;
      if (gs.t >= st.occ.until || !live) looLeaves(st);
    } else if (live && gs.t >= st.next && !leaver && hyp(player.x, player.z, POS.looFront.x, POS.looFront.z) > 3) {
      const who = weighted(looCandidates());
      st.occ = { who, until: gs.t + rnd(...LOO_WHO[who].mins), knocks: 0 };
      if (hyp(player.x, player.z, POS.loo.x, POS.loo.z) < 25) sfx('door', POS.loo.x, POS.loo.z);
    }
    looSignMat.color.setHex(st.occ ? 0xd83a2e : 0x2fbf5a);
    if (leaver) {
      const L2 = leaver, u = L2.m.userData;
      L2.t += dt;
      const tgt = L2.path[L2.seg];
      const dx = tgt.x - L2.m.position.x, dz = tgt.z - L2.m.position.z, d = Math.hypot(dx, dz);
      if (d < 0.4) L2.seg++;
      else {
        const step2 = Math.min(d, 1.35 * dt);
        L2.m.position.x += (dx / d) * step2; L2.m.position.z += (dz / d) * step2;
        L2.m.rotation.y = Math.atan2(dx, dz);
        u.phase += step2 * 4.5;
        u.legL.rotation.x = Math.sin(u.phase) * 0.5; u.legR.rotation.x = -Math.sin(u.phase) * 0.5;
        u.armL.rotation.x = -Math.sin(u.phase) * 0.35; u.armR.rotation.x = Math.sin(u.phase) * 0.35;
      }
      if (L2.seg >= L2.path.length || L2.t > 40) {
        scene.remove(L2.m);
        if (L2.who === 'pump') pumpGuy.visible = !!(gs.pumpHere && pump.visible && live);
        leaver = null;
      }
    }
  }
  /** Out he comes: the flush, the door, a word if you're near, and off back to where he's from, round the slab. */
  function looLeaves(st) {
    const o = st.occ, w = LOO_WHO[o.who];
    st.occ = null;
    st.next = gs.t + rnd(35, 90);
    st.ripe = gs.t + 12;
    if (!['prep', 'pipes', 'pour', 'wash', 'cure'].includes(gs.phase)) return;
    const near2 = hyp(player.x, player.z, POS.loo.x, POS.loo.z);
    if (near2 < 30) { sfx('flush', POS.loo.x, POS.loo.z); setTimeout(() => sfx('door', POS.loo.x, POS.loo.z), 900); }
    const m = makePerson(Object.assign({ g: 'm' }, w.look));
    m.traverse((x) => { if (x.isMesh) x.castShadow = true; });
    m.position.set(POS.looFront.x, 0, POS.looFront.z);
    scene.add(m);
    const dest = o.who === 'pump' ? P(pumpGuy.position.x, pumpGuy.position.z) : o.who === 'truck' ? P(mixGuy.position.x, mixGuy.position.z)
      : o.who === 'kebab' ? POS.kioskFront : o.who === 'office' ? POS.office : P(POS.looFront.x * 1.6, POS.looFront.z * 1.6);
    leaver = { m, who: o.who, path: routeRound(POS.looFront, dest), seg: 0, t: 0 };
    if (near2 < 16) {
      const l = fresh(L.looOut);
      setTimeout(() => { toast(`${w.name}, coming out: ${l}`); say(l, looVoice(o.who)); }, 1200);
    }
  }
  /** A walk from a to b that goes round the slab (and its boards), not across it. */
  function routeRound(a, b) {
    const bx = site.box, pad = 1.6;
    const x0 = bx.x0 - pad, x1 = bx.x1 + pad, z0 = bx.z0 - pad, z1 = bx.z1 + pad;
    const hits = (p, q) => {
      // the segment against the padded box, by sampling: plenty for a walk across a yard
      for (let k = 1; k < 24; k++) { const t = k / 24, x = lerp(p.x, q.x, t), z = lerp(p.z, q.z, t); if (x > x0 + 0.05 && x < x1 - 0.05 && z > z0 + 0.05 && z < z1 - 0.05) return true; }
      return false;
    };
    if (!hits(a, b)) return [P(b.x, b.z)];
    const cs = [P(x0, z0), P(x1, z0), P(x1, z1), P(x0, z1)];
    let best = null, bl = 1e9;
    cs.forEach((c) => { if (!hits(a, c) && !hits(c, b)) { const l = hyp(a.x, a.z, c.x, c.z) + hyp(c.x, c.z, b.x, b.z); if (l < bl) { bl = l; best = [c, P(b.x, b.z)]; } } });
    if (best) return best;
    cs.forEach((c, k) => { const c2 = cs[(k + 1) % 4]; [[c, c2], [c2, c]].forEach(([p, q]) => { if (!hits(a, p) && !hits(q, b)) { const l = hyp(a.x, a.z, p.x, p.z) + hyp(p.x, p.z, q.x, q.z) + hyp(q.x, q.z, b.x, b.z); if (l < bl) { bl = l; best = [p, q, P(b.x, b.z)]; } } }); });
    return best || [P(b.x, b.z)];
  }
  function useLoo() {
    const st = gs.loo || (gs.loo = { occ: null, next: gs.t + 30 });
    const n = gs.needs;
    if (st.occ) {
      // pull the knob: it rattles, and whoever's in there has something to say about it
      st.occ.knocks++;
      sfx('rattle', POS.loo.x, POS.loo.z);
      const w = LOO_WHO[st.occ.who];
      let l;
      if (st.occ.knocks >= 3 && !st.occ.fumed) { st.occ.fumed = true; l = fresh(L.looFume); st.occ.until += 2; }
      else l = fresh(L.looInside.concat(L.looInsideBy[st.occ.who] || []));
      toast(`${w.name}, from inside: ${l}`, 'warn');
      say(l, looVoice(st.occ.who));
      if (n.wee > 70 && !gs.accident) toastOnce('behindVan', 'Or behind the van. Everybody does. Nobody admits it.', '', 60000);
      return false;
    }
    sfx('door', POS.loo.x, POS.loo.z);
    if (st.ripe && gs.t < st.ripe) toastOnce('ripe', 'You open the door. The air inside has a texture. You go in anyway. You have to.', 'warn', 60000);
    if (gs.accident) toastOnce('lateLoo', 'Bit late for the toilet now. It\'s the water tank and the spare trousers you want.', 'warn', 60000);
    if (n.poo > 45) {
      simulate(12, awayNow());
      toast(fresh(L.looTwo));
      n.poo = rnd(0, 8); n.wee = rnd(0, 6);
    } else if (n.wee > 25) {
      simulate(3, awayNow());
      toast(fresh(L.looOne));
      n.wee = rnd(0, 6);
    } else {
      simulate(4, awayNow());
      toast('You don\'t need to. You go in anyway, for four minutes of peace and quiet. It\'s the best four minutes of the day.');
    }
    n.warned = {};
    setTimeout(() => sfx('flush', POS.loo.x, POS.loo.z), 400);
    setTimeout(() => sfx('door', POS.loo.x, POS.loo.z), 1100);
    return true;
  }
  /** Round the side of the van where the road can't see. Mostly can't see. */
  function pissBehindVan() {
    const n = gs.needs;
    simulate(2, awayNow());
    n.wee = rnd(0, 6);
    n.warned.wee70 = n.warned.wee90 = false;
    sfx('trickle');
    if (chance(0.4)) {
      toast('Behind the van. The neighbour sees. The pump driver sees. The pump driver films it.', 'warn');
      remember('You pissed behind the van. There is a video.');
      charge('Pissing behind the van (the neighbour called someone)', 30);
    } else toast('Behind the van. Nobody saw. You\'re almost sure nobody saw.');
    return true;
  }
  /** The spare clothes behind the driver's seat: trousers for the worst day, boots for a dirty one. */
  function changeClothes() {
    const a = gs.accident, did = [];
    if (a && a.kind === 'shit' && !a.washed) { toast('Not over that. Hose yourself down at the water tank first, or the spares go the same way.', 'warn'); return false; }
    if (a) {
      if (!gs.spareTrousers) { gs.spareTrousers = true; did.push('trousers'); } else did.push('binbag');
      gs.accident = null;
    }
    if (!gs.spareBoots && (gs.boots || 0) > 0.2) { gs.spareBoots = true; gs.boots = 0; gs.bootsFloor = 0; gs.bootsAt = gs.t; did.push('boots'); }
    if (!did.length) { toast('The spares are for when you need them. You don\'t. Yet.'); return false; }
    sfx('door', POS.vanSeat.x, POS.vanSeat.z);
    simulate(3, awayNow());
    const say2 = {
      trousers: 'You change into the spare trousers behind the van door. They\'re from 2019. They\'re tight. They\'re dry.',
      binbag: 'No more spare trousers. You wear a bin bag, tied at the waist with rebar wire. It rustles when you walk.',
      boots: 'You change into the spare boots. Clean, dry, and about a size too small. The old ones go in a bag, rigid.',
    };
    toast(did.map((d) => say2[d]).join(' '), 'good');
    if (did.includes('binbag')) remember('You spent the afternoon in a bin bag.');
    return true;
  }

  function tryGoHome() {
    if (gs.packing) return false;
    if (gs.gaveUp || (gs.H >= 99.9 && !slabFinished())) { gs.gaveUp = true; packUp(); return true; }
    const missing = [];
    if (gs.H < 95) missing.push(`It's only ${Math.floor(gs.H)}% hard. 95% or you sleep here.`);
    if (!gs.panPasses.length) missing.push('No pan pass yet.');
    if (!gs.bladePasses.length) missing.push('No blade pass yet.');
    if (edgeWorkLeft()) missing.push(`${edgeWorkText()} still to trowel.`);
    if ((gs.prep.laser || gs.laserSetup) && !gs.laserPacked) missing.push('The laser is still out. Pack it up and put it in the van.');
    if (missing.length) {
      modal({ personal: true, who: 'Foreman, in your head', title: 'Not yet.', text: missing.join('\n'), choices: [{ label: 'Fine', primary: true }] });
      return false;
    }
    const out = toolsOut(), dirty = dirtyTools();
    if (out.length || dirty.length) {
      modal({
        personal: true, who: 'Before you go', title: 'The van isn\'t packed.', sound: 'buzz',
        text: [out.length ? `Still out on site: ${theList(out)}.` : '', dirty.length ? `Still covered in concrete: ${theList(dirty)}.` : ''].filter(Boolean).join('\n') +
          '\n\nEvery tool goes back in the van, washed. The manager checks. The manager always checks.',
        choices: [
          { label: 'Go back for them', primary: true },
          { label: 'Leave it. Go home.', danger: true, fn: () => leaveTheMess(out, dirty) },
        ],
      });
      return false;
    }
    endDay();
    return true;
  }

  /** The slab's work is done: pans, blades, and every edge. (The laser is only a tool.) */
  function slabFinished() { return gs.panPasses.length > 0 && gs.bladePasses.length > 0 && !edgeWorkLeft(); }
  /** 100% and not finished: nothing more will go on this slab today. */
  function tooLate() {
    gs.gaveUp = true;
    const what = [];
    if (!gs.panPasses.length) what.push('no pan pass');
    if (!gs.bladePasses.length) what.push('no blade pass');
    if (edgeWorkLeft()) what.push(`${edgeWorkText()} still rough`);
    remember('It went to 100% before the slab was done.');
    modal({
      who: 'Hardness 100%', title: 'It\'s gone off.', sound: 'buzz', voice: 'foreman',
      text: L.tooLate.text.replace('{what}', what.join(', ')),
      choices: [
        { label: 'Throw everything in the van', primary: true, fn: () => packUp() },
        { label: 'Stare at it a bit longer', fn: () => toast(fresh(L.tooLate.stare), 'warn') },
      ],
    });
  }

  // ------------------------------------------------------------------ throwing it all in the van
  // You march to the van and throw the tools in, one after another, the machines last. Each flies
  // from your hand in an arc and lands in the van with a noise; the van rocks. Then the door, the
  // engine, and the day is over.
  // the van's side door faces north-ish; the tools go through it, and you throw from out in front of it
  const VAN_IN = new THREE.Vector3(-24, 1.3, 9);       // the open back of the van (set with the layout)
  const VAN_THROW = P(-18, 8);                          // where you stand to throw
  // the side door, slid open: a dark hole in the side of the van, and the door panel slid back
  let vanRock = 0;
  function packUp() {
    if (gs.packing) return;
    if (held()) putDown(true);
    gs.waitMode = null; gs.fastForward = null; showWait();
    // stand back from the van, facing the door, which is open
    player.x = VAN_THROW.x; player.z = VAN_THROW.z;
    vanBack.want = 1;
    $('#buttons').style.visibility = 'hidden';
    $('#btnFlip').style.visibility = 'hidden';
    $('#targetInfo').style.visibility = 'hidden';
    player.yaw = Math.atan2(-(VAN_IN.x - player.x), -(VAN_IN.z - player.z));
    player.pitch = 0.08;
    const items = [];
    ['hammer', 'pliers', 'cutter', 'handTrowel', 'float', 'shovel'].forEach((id) => {
      const t = gs.tools[id];
      if (t && t.in === 'ground' && lying[id] && !atVan(t.x, t.z)) items.push({ id, obj: lying[id], machine: false });
    });
    if (tripod.visible || gs.carrying === 'laser') { gs.carrying = null; items.push({ id: 'laser', obj: tripod, machine: false }); }
    ['trowelSmall', 'trowelBig'].forEach((id) => {
      const t = gs.tools[id];
      if (t && t.in === 'ground' && !atVan(t.x, t.z)) items.push({ id, obj: machines[id].group, machine: true });
    });
    const t0 = 0.9;
    items.forEach((it, k) => { it.at = t0 + k * 0.65; it.dur = it.machine ? 1.25 : 0.85; });
    gs.packing = { t: 0, items, doneAt: (items.length ? items[items.length - 1].at + items[items.length - 1].dur : t0) + 1.2, stage: 0, hand: 0, machine: 0 };
    toast(fresh(L.packUp.start), 'warn');
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
        if (it.machine && !pk.saidMachine) { pk.saidMachine = true; toast(fresh(L.packUp.machine), 'warn'); }
        else if (it.id === 'laser') toast(L.packUp.laser);
        else if (!it.machine && !pk.saidHand) { pk.saidHand = true; setTimeout(() => toast(fresh(L.packUp.hand)), 500); }
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
    if (pk.stage === 0 && pk.t >= pk.doneAt) { pk.stage = 1; sfx('door', VAN_IN.x, VAN_IN.z); vanRock = 0.6; vanBack.want = 0; toast(L.packUp.end); }
    if (pk.stage === 1 && pk.t >= pk.doneAt + 0.9) { pk.stage = 2; sfx('engine', VAN_IN.x, VAN_IN.z); }
    if (pk.stage === 2 && pk.t >= pk.doneAt + 2.4) {
      gs.thrown = { hand: pk.hand, machine: pk.machine };
      remember(pk.hand + pk.machine ? `You threw ${pk.hand + pk.machine} tools in the van and drove off without looking back.` : 'You drove off without looking back.');
      gs.packing = null;
      $('#buttons').style.visibility = '';
      $('#btnFlip').style.visibility = '';
      $('#targetInfo').style.visibility = '';
      endDay();
    }
  }

  // ------------------------------------------------------------------ troweling
  function passCoverage(key) { return gs.cells.filter((c) => c[key]).length / gs.cells.length; }
  function completePass(kind) {
    // A pass is judged square by square, on how hard each was when the machine went over it: on a
    // long pour the first truck's end is panned mid-pour and the last one's after, each in its time.
    const key = kind === 'pan' ? 'covP' : 'covB', [lo, hi] = kind === 'pan' ? [25, 65] : [55, 92];
    const hs = gs.cells.map((c) => (c[key] && c[key + 'H'] !== undefined ? c[key + 'H'] : cellH(c)));
    const H = hs.reduce((a, b) => a + b, 0) / (hs.length || 1);
    const inWin = hs.filter((h) => h >= lo && h <= hi).length / (hs.length || 1);
    const most = inWin >= 0.75, part = inWin < 0.95 && most ? ` ${Math.round(inWin * 100)}% of it in the window.` : '';
    let verdict, good;
    if (kind === 'pan') {
      good = most;
      verdict = most ? `In the window.${part} ±${rms().toFixed(1)} mm now.` : H < lo + 5 ? 'Too early: the pans dug in and made waves.' : H > hi - 5 ? 'Late pan pass: skated over the top. Better than nothing.' : `Half of it too soft, half too hard: only ${Math.round(inWin * 100)}% in the window.`;
      gs.cells.forEach((c) => { c.pan++; c.covP = false; delete c.covPH; });
      gs.panPasses.push({ H, good });
    } else {
      good = most && gs.panPasses.length > 0;
      verdict = !gs.panPasses.length ? 'Blades with no pan pass first. It shines, but it isn\'t flat.' : most ? `In the window.${part} It's starting to shine.` : H < lo + 5 ? 'Blades too early: tore the paste.' : H > hi - 5 ? 'Blades on a slab that\'s already hard. You\'re polishing a stone.' : `Blades on a patchy slab: only ${Math.round(inWin * 100)}% of it in the window.`;
      gs.cells.forEach((c) => { c.blade++; c.covB = false; delete c.covBH; });
      gs.bladePasses.push({ H, good });
    }
    cellsDirty = true;
    surfDirty = true;
    sfx(good ? 'chime' : 'buzz');
    const passLine = `${kind === 'pan' ? 'Pan' : 'Blade'} pass ${kind === 'pan' ? gs.panPasses.length : gs.bladePasses.length} done at ${Math.floor(H)}%. ${verdict}`;
    toast(passLine, good ? 'good' : 'warn');
    if (isHost() && net.started) netSend({ t: 'toast', text: passLine, kind: good ? 'good' : 'warn' });
  }

  /** Home with tools still out or filthy: the manager has seen the photos by the time you're in the car. */
  function leaveTheMess(out, dirty) {
    gs.leftBehind = out.slice();
    gs.dirtyAtEnd = dirty.slice();
    gs.yelledTools = true;
    const parts = [];
    if (out.length) parts.push(fresh(L.leftTools).replace('{t}', theList(out)));
    if (dirty.length) parts.push(fresh(L.dirtyTools).replace('{d}', theList(dirty)));
    remember(`You went home with ${out.length ? theList(out) + ' still on site' : ''}${out.length && dirty.length ? ', and ' : ''}${dirty.length ? theList(dirty) + ' covered in concrete' : ''}. The manager called before you reached the car.`);
    modal({
      who: 'The manager, on the phone', title: 'He saw the photos.', sound: 'ring', voice: 'manager',
      text: parts.join('\n\n'),
      choices: [{ label: 'Hang up and drive', primary: true, fn: () => endDay() }],
    });
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
    let score = 1000 - dev * 22 - defects * 18 - (gs.yelled ? 60 : 0) - (gs.gaveUp ? 80 : 0) - (site.edges.length - Math.min(gs.edgesDone, site.edges.length)) * 12 - edgeLeft() * 3 - gs.edgeLate * 2 - (gs.wrongLoad ? L.wrong[gs.wrongLoad].cost : 0) - late * 1.5 - gs.waste * 35 - gs.truckWaitPaid * 0.5 - gs.stats.falls * 10 - gs.edgeNotes.length * 12 - (gs.stats.accidents || 0) * 40
      + Math.min(goodPans, 3) * 40 + Math.min(goodBlades, 3) * 40 + gs.stats.hell * 5;
    score = Math.round(clamp(score, 0, 1200));
    const rank = score >= 950 ? 'Slab wizard' : score >= 800 ? 'Proper concrete person' : score >= 620 ? 'Adequate slab operator' : score >= 420 ? 'Footprint curator' : 'The dog\'s favourite';
    const best = Number(store('pourday.best') || 0);
    if (score > best) store('pourday.best', String(score));
    $('#eRank').textContent = rank;
    $('#eScore').textContent = `${score} points` + (score > best ? ' · new best' : best ? ` · best ${best}` : '');
    if (bootsSet() && !gs.bootsCharged) { gs.bootsCharged = true; charge('Boots set solid in concrete: a new pair', 75); }
    vanFloor();
    const rows = [
      ['Played for', playedFor()],
      ['Arrived', clock(gs.arrived) + (late > 15 ? ` (${dur(late)} late)` : '')],
      ['Poured', clock(gs.pourEnd)],
      ['Home', clockDay(gs.t)],
      ['Shift', dur(gs.t - gs.arrived)],
      ['Weather', `${day.baseTemp.toFixed(0)} °C · ${day.rh}% RH · wind ${day.wind} m/s`],
      ['Slab', `${day.area} m² · ${day.thick} mm · ${volumeNeeded().toFixed(1)} m³`],
      ['Flatness', `±${dev.toFixed(1)} mm`],
      ['Pan / blade passes', `${gs.panPasses.length} / ${gs.bladePasses.length} (${goodPans + goodBlades} in the window)`],
      ['Marks left in it', String(defects)],
      ['Concrete wasted', `${gs.waste.toFixed(1)} m³` + (gs.stats.unspilled ? ' (after shovelling some back)' : '')],
      ['Truck waiting time', `${Math.round(gs.truckWaitPaid)} €`],
      ['People told to go to hell', String(gs.stats.hell)],
      ['Concrete', gs.wrongLoad ? L.wrong[gs.wrongLoad].row : 'as ordered'],
      ['Phone call with the manager', gs.yelled ? `yes, about ${gs.yelled} marks. Loud.` : 'none, thank God'],
      ['Times on your butt', String(gs.stats.falls)],
      ...(gs.stats.accidents ? [['Accidents', `${gs.stats.accidents} (don't ask)`]] : []),
      ['Boots', bootsRow()],
      ['Fingers given', String(gs.stats.flips)],
      ['Tools', gs.leftBehind.length || gs.dirtyAtEnd.length ? `${gs.leftBehind.length} left on site, ${gs.dirtyAtEnd.length} dirty` : 'all in the van, clean'],
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
    $('#btnAgain').hidden = false;
    if (isHost() && net.started) {
      netSend({ t: 'end', rank: $('#eRank').textContent, score: $('#eScore').textContent, pay: $('#ePay').innerHTML, stats: $('#eStats').innerHTML, story: $('#eStory').innerHTML, verdict: lastVerdict });
    }
  }

  let lastVerdict = '';
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
    if (edgeWorkLeft()) cut('roughEdges', (site.edges.length - gs.edgesDone) * 10 + edgeLeft() * 3, { n: edgeWorkText() });
    if (gs.edgeLate) cut('edgesLate', gs.edgeLate * 2, { n: gs.edgeLate });
    gs.charges.forEach(([label, eur]) => lines.push([label, -Math.round(eur)]));
    if (gs.leftBehind.length) cut('leftTools', gs.leftBehind.reduce((a, id) => a + (isMachine(id) ? 150 : 25), 0), { n: gs.leftBehind.length });
    if (gs.dirtyAtEnd.length) cut('dirtyTools', gs.dirtyAtEnd.reduce((a, id) => a + (isMachine(id) ? 45 : 15) * (dirtSet(id) ? 2 : 1), 0), { n: gs.dirtyAtEnd.length });
    if (gs.thrown && gs.thrown.hand + gs.thrown.machine) cut('thrown', gs.thrown.hand * 5 + gs.thrown.machine * 35, { n: gs.thrown.hand + gs.thrown.machine });
    if (gs.stats.hell) lines.push([fillIn(pick(P2.hell), { n: gs.stats.hell }), 0]);
    if (gs.stats.flips) lines.push([`Fingers given: ${gs.stats.flips}. No charge, but HR has been told`, 0]);
    if (gs.coldJoint > 3) lines.push([pick(['Cold joint between two loads. The slab will crack along it, at your expense, eventually', 'Two loads that never met properly. Like your parents']), -60]);
    if (gs.spilled > 0.02) lines.push([`Concrete over the formwork: ${gs.spilled.toFixed(2)} m³ in the gravel. The worms have a floor now`, 0]);
    if (gs.stats.pukeSlab) lines.push([pick(['Grinding out a "personal contribution" from the slab', 'Removing last night from the concrete', 'Biohazard surcharge. The client asked what the yellow bit is']), -40 * gs.stats.pukeSlab]);
    else if (gs.stats.puked) lines.push([pick(['Hangover: no charge. The foreman smelled it from the road', 'Threw up on the gravel. The gravel didn\'t complain. Nobody else is happy either']), 0]);
    if (inTeam()) {
      const crew = [[net.name, gs.stats.prints]].concat([...net.crew.values()].map((c) => [c.name, c.prints]));
      crew.sort((a, b) => b[1] - a[1]);
      lines.push([`Crew: ${crew.map((c) => c[0]).join(', ')}. The day rate is each`, 0]);
      if (crew[0][1] > 0) lines.push([`Footprints: ${crew.map((c) => `${c[0]} ${c[1]}`).join(', ')}. ${crew[0][0]} buys the coffee`, 0]);
    }
    if (goodBlades >= 2 && rep.sd < 3) lines.push([pick(P2.shine), 40]);
    const base = 220, takeHome = base + lines.reduce((s, l) => s + l[1], 0);
    const owe = P2.verdictBad.filter((v) => /owe us/.test(v)), bad = P2.verdictBad.filter((v) => !/owe us/.test(v));
    const verdict = pick(takeHome < 0 ? owe : takeHome >= 180 ? P2.verdictGood : takeHome <= 60 ? bad : P2.verdictMeh);
    const eur = (x) => (x < 0 ? '−' : x > 0 ? '+' : '') + '€' + Math.abs(x);
    const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;');
    const stampText = takeHome < 0 ? 'You owe us' : takeHome < base * 0.6 ? 'Docked' : '';
    $('#ePay').innerHTML = `<h4><span>Pay slip</span><span>${clock(gs.arrived)}–${clock(gs.t)}</span></h4>`
      + `<div class="row"><span>${esc(P2.base)}</span><b>€${base}</b></div>`
      + lines.map(([t, x]) => `<div class="row"><span>${esc(t)}</span><b class="${x < 0 ? 'neg' : x > 0 ? 'pos' : 'zero'}">${x ? eur(x) : 'no charge'}</b></div>`).join('')
      + `<div class="net"><span>Take home</span><b class="${takeHome < 0 ? 'neg' : ''}">${takeHome < 0 ? '−' : ''}€${Math.abs(takeHome)}</b></div>`
      + `<div class="verdict">${esc(verdict)}</div>`
      + (stampText ? `<div class="stampx">${stampText}</div>` : '');
    lastVerdict = verdict;
    setTimeout(() => say(verdict, 'manager'), 900);
    return takeHome;
  }

  // ------------------------------------------------------------------ picking tools up, putting them down
  let nearTool = null, wantTool = null;
  function held() { return gs.tool === 'hands' ? null : gs.tool; }
  function isMachine(id) { return !!(id && TOOLS[id] && TOOLS[id].machine); }
  function unloadTools() {
    TOOL_IDS.forEach((id) => {
      if (id === 'hose' || (id === 'rideOn' && day.area <= 50)) return;
      const [x, z, yaw] = TOOL_HOME[id];
      gs.tools[id] = { in: 'ground', x, z, yaw };
    });
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
    delete t.by;
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
  // what each hand tool sounds like, picked up and put down
  const TOOL_MAT = { hammer: 'steel', pliers: 'steel', cutter: 'steel', handTrowel: 'steel', shovel: 'shovel', float: 'alu', hose: 'rubber' };
  function pickUp(id) {
    if (held()) putDown(true);
    const t = gs.tools[id];
    t.in = 'hand';
    t.by = net.me;
    gs.tool = id;
    if (TOOLS[id].ride) {
      player.x = t.x + Math.sin(t.yaw) * 0.3;
      player.z = t.z + Math.cos(t.yaw) * 0.3;
      player.yaw = t.yaw;
      sfx('pullstart');
    } else if (isMachine(id)) {
      player.yaw = Math.atan2(-(t.x - player.x), -(t.z - player.z));
      sfx('pullstart');
    } else sfx(TOOL_MAT[id] ? 'pick_' + TOOL_MAT[id] : 'pickup');
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
  let pourOut = null;         // the hose aimed just past the formwork: { x, z, c: the edge square, inside: share that still lands in }
  let nearMarker = null;
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
    if (w) return { kind: 'shout', label: w.kind === 'driver' ? (w.isHelper ? `Talk to ${helper.name}` : w.crewId ? `Shout at ${w.name}` : 'Talk') : w.kind === 'dog' ? (gs.sausage ? 'Throw the sausage' : 'Shoo!') : 'Oi! Off the slab!', w };
    const t = gs.tool;
    edgeSpot = null;
    if (gs.phase === 'pour') {
      if (t === 'hose') {
        if (gs.blocked >= 0) return { kind: 'none', label: 'Line blocked' };
        if (!gs.truck || gs.truck.waiting) return { kind: 'none', label: 'No concrete' };
        if (target) return { kind: 'pour', label: 'Hold: pour' };
        if (pourOut) return { kind: 'pour', label: pourOut.inside ? 'Hold: pour (onto the boards)' : 'Hold: pour (into the gravel)' };
        return { kind: 'none', label: 'Aim at the slab' };
      }
      if (t === 'float') {
        // wet, it moves and levels; once that end has started to go off it only closes the top
        if (target && target.fill > 5 && cellH(target) < 18) return { kind: 'level', label: 'Hold: float' };
        if (target && target.fill > 20) return { kind: 'repair', label: target.marks.length ? 'Hold: float out marks' : 'Hold: float (it\'s going off)' };
        return { kind: 'none', label: 'Aim at concrete' };
      }
      // an empty hand on concrete that has been in a while: how far has it gone?
      if (t === 'hands' && target && target.fill > 20 && mixOf(target)) return { kind: 'thumb', label: 'Thumb test' };
      if (t === 'hands' && gs.tools.hose && gs.tools.hose.in === 'ground') return { kind: 'none', label: 'Get the hose' };
    }
    if (t === 'shovel' && (gs.phase === 'pour' || gs.phase === 'wash' || gs.phase === 'cure') && (target ? cellH(target) : gs.H) < 30) {
      // concrete over the boards and into the gravel: dig it back out while it's wet
      const sp = !target && gs.H < 30 ? spillInReach() : null;
      if (sp) return { kind: 'unspill', label: 'Hold: shovel the spill back in', blob: sp };
      if (target && target.fill > day.thick + 4) return { kind: 'shovel', label: 'Hold: shovel off the extra' };
      // it digs wherever there's wet concrete to dig — including where it shouldn't
      if (target && target.fill > 25) return { kind: 'shovel', label: target.fill < day.thick - 15 ? 'Hold: dig the hole deeper' : 'Hold: shovel (it\'s level already)' };
      if (target) return { kind: 'none', label: 'Nothing to shovel here' };
      return { kind: 'none', label: 'Aim at a high spot' };
    }
    // After the pour the slab is yours to work straight away, whether the tools are washed yet or
    // not: waiting for the washing-up to be done left the float and the trowels doing nothing
    // until the slab had gone off far enough to start the cure on its own. And on a long pour the
    // first truck's end goes off while the last is still coming: that end can take the trowels,
    // the edges and the corners while the pour goes on.
    if (gs.phase === 'cure' || gs.phase === 'wash' || gs.phase === 'pour') {
      const inn = target && concreteIn(target._hx, target._hz);
      if (t === 'float' && inn) return { kind: 'repair', label: target.marks.length ? 'Hold: float out marks' : 'Hold: float' };
      if (t === 'handTrowel' && inn) {
        // against a board that's still rough: along the board; anywhere else, the marks
        // once down on one knee at it, the eye is lower and the look lands nearer: it stays the edge
        const e = cellH(target) >= 10 ? openSide(target, target._hx, target._hz, input.action && lastCtxKind === 'edge' ? 0.9 : 0.45) : null;
        if (e) { edgeSpot = e; return { kind: 'edge', label: 'Hold: edge along the board' }; }
        return { kind: 'repair', label: target.marks.length ? 'Hold: trowel out marks' : 'Hold: hand trowel' };
      }
      // the machine runs out ahead of you, so it is where its discs are that counts
      if (isMachine(t)) {
        const on = discs().some((d) => concreteIn(d.x, d.z));
        if (on) return { kind: 'trowel', label: `Hold: ${gs.fit[t] === 'pans' ? 'pan' : 'blade'} pass${t === 'trowelSmall' ? ' · edges' : ''}` };
        if (gs.phase === 'pour' && discs().some((d) => onSlab(d.x, d.z))) return { kind: 'none', label: 'Nothing poured here yet' };
        return { kind: 'none', label: TOOLS[t].ride ? 'Drive onto the slab' : 'Steer it onto the slab' };
      }
      if (t === 'hands' && inn && gs.phase !== 'pour') return { kind: 'thumb', label: 'Thumb test' };
    }
    return { kind: 'none', label: '—' };
  }

  /** A job done in the wet: the pour's burst formwork, mesh in the concrete, a tie or a cut after the pour. */
  function jobInConcrete(m) {
    const t = m.tool && m.tool !== 'hands' ? m.tool : held();
    if (!t || isMachine(t)) return;
    const c = cellAt(m.x, m.z);
    const wet = m.id === 'blowout' || m.id === 'rebarUp' || (c && c.fill > 10 && cellH(c) < 90);
    if (!wet) return;
    addDirt(t, m.id === 'blowout' ? 0.6 : 0.4);
    const the = TOOLS[t].the, are = t === 'pliers';
    toastOnce('jobdirt' + t, `${the[0].toUpperCase()}${the.slice(1)} came out of that grey. Wash ${are ? 'them' : 'it'} before it sets.`, '', 40000);
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
        if (ok !== false) netMarker(m.id);
        if (ok !== false) jobInConcrete(m);
        if (ok !== false && !m.active()) m.group.visible = false;
      }
      return;
    }
    if (ctx.kind === 'shout') { if (input.actionTapped) shoutAt(ctx.w); return; }
    if (ctx.kind === 'pour') pourTick(dt);
    else if (ctx.kind === 'level') levelTick(target, dt);
    else if (ctx.kind === 'repair') repairTick(target, dt);
    else if (ctx.kind === 'edge') { if (edgeSpot) edgeTick(edgeSpot, dt); }
    else if (ctx.kind === 'trowel') trowelTick(dt);
    else if (ctx.kind === 'shovel') shovelTick(target, dt);
    else if (ctx.kind === 'unspill') unspillTick(ctx.blob, dt);
    else if (ctx.kind === 'thumb' && input.actionTapped) thumb(target);
  }

  let paintT = 0;
  function pourTick(dt) {
    if ((!target && !pourOut) || !gs.truck || gs.truck.left <= 0) return;
    const speed = gs.mixState === 'stiff' ? 90 : gs.mixState === 'soup' ? 125 : 110;
    const add = speed * dt;
    if (!target) { pourPast(pourOut, add, dt); return; }
    const nb = neighbours(target);
    const spread = gs.mixState === 'soup' ? 0.4 : 0.25;
    target.fill += add * (1 - spread);
    addLoad(target, add * (1 - spread));
    nb.forEach((n) => { n.fill += (add * spread) / nb.length; addLoad(n, (add * spread) / nb.length); });
    const m3 = add / 1000;
    if (isGuest()) net.pourM3 += m3;
    else { gs.truck.left -= m3; gs.pouredM3 += m3; }
    spillOver(target);
    nb.forEach(spillOver);
    cellsDirty = true;
    paintT -= dt;
    if (paintT <= 0) {
      paintT = 0.07;
      paintPour(target._hx + rnd(-0.2, 0.2), target._hz + rnd(-0.2, 0.2));
      if (gs.wrongLoad === 'fibre') paintFibres(target._hx, target._hz);
    }
    if (isGuest()) return;
    pourTrouble(dt);
    if (gs.truck.left <= 0) truckEmpty();
  }
  /** The hose pointed past the formwork: it goes where it's pointed. Over the top of the board, half of it still gets in. */
  function pourPast(o, add, dt) {
    const m3 = add / 1000;
    if (isGuest()) net.pourM3 += m3;
    else { gs.truck.left -= m3; gs.pouredM3 += m3; }
    const inn = add * o.inside;
    if (inn) { o.c.fill += inn; addLoad(o.c, inn); spillOver(o.c); cellsDirty = true; }
    spillAt(o.x, o.z, (add - inn) / 1000, o.c, o.di, o.dj);
    if (chance(dt * 6)) emit(o.x, 0.05, o.z, rnd(-0.6, 0.6), rnd(0.3, 0.9), rnd(-0.6, 0.6), 0.6, 0x7d7f80, rnd(0.04, 0.07));
    if (isGuest()) return;
    pourTrouble(dt);
    if (gs.truck.left <= 0) truckEmpty();
  }
  /** Where the hose lands when it isn't on the slab: the edge square it's just past, if it's near enough to count. */
  function pastTheBoards(x, z) {
    let best = null, bd = 1.1;
    for (const c of gs.cells) {
      const dx = Math.max(Math.abs(x - gx(c.i) - 0.5) - 0.5, 0), dz = Math.max(Math.abs(z - gz(c.j) - 0.5) - 0.5, 0);
      const d = Math.hypot(dx, dz);
      if (d < bd) { bd = d; best = c; }
    }
    if (!best) return null;
    const ox = x - gx(best.i) - 0.5, oz = z - gz(best.j) - 0.5;
    const di = Math.abs(ox) >= Math.abs(oz) ? Math.sign(ox) : 0, dj = di ? 0 : Math.sign(oz);
    // the stream is as wide as your hand: right on the board, half of it goes each way
    return { x, z, c: best, di, dj, inside: bd < 0.1 ? 0.5 : 0, d: bd };
  }

  // ------------------------------------------------------------------ over the formwork
  // The boards stand 60 mm proud of the slab. Pour hard against one and it doesn't stop there: it
  // goes over the top, into the gravel, onto the waste line and into the manager's ear.
  const FORM_UP = 60;
  const spillBlobs = new THREE.Group();
  scene.add(spillBlobs);
  const spillMat = new THREE.MeshLambertMaterial({ color: 0x6c7072 });
  let spillSaid = 0;
  /** The ways out of a square that have a board in them, as unit steps. */
  function boardsOf(c) {
    if (c.boards) return c.boards;
    c.boards = [[1, 0], [-1, 0], [0, 1], [0, -1]].filter(([di, dj]) => !isOn(c.i + di, c.j + dj));
    return c.boards;
  }
  // ------------------------------------------------------------------ the edges
  // Every metre of board round the slab gets edged: the concrete against it closed and its arris
  // rounded, with the edge trowel run along the board or the hand trowel, kneeling. A power
  // trowel can't get there, and a rough edge is the first thing the client's finger finds.
  // Each square keeps its done sides as bits; the corners and pipe collars are jobs of their own.
  function sideBit(di, dj) { return di > 0 ? 1 : di < 0 ? 2 : dj > 0 ? 4 : 8; }
  /** Metres of board edged, and metres of board. */
  function edgeMetres() {
    let done = 0, all = 0;
    for (const c of gs.cells) for (const [di, dj] of boardsOf(c)) { all++; if (c.edged & sideBit(di, dj)) done++; }
    return [done, all];
  }
  function edgeLeft() { const [d, a] = edgeMetres(); return a - d; }
  /** Everything round the outside still to do: metres of edge, corners and collars. */
  function edgeWorkLeft() { return edgeLeft() + site.edges.length - gs.edgesDone; }
  /** The same in words: "12 m of edge and 3 corners". */
  function edgeWorkText() {
    const m = edgeLeft(), c = site.edges.length - gs.edgesDone;
    const col = site.edges.filter((e) => !e.done && e.kind === 'collar').length, cor = c - col;
    return [m ? `${m} m of edge` : '', cor ? `${cor} corner${cor > 1 ? 's' : ''}` : '', col ? `${col} pipe collar${col > 1 ? 's' : ''}` : ''].filter(Boolean).join(', ').replace(/, ([^,]*)$/, ' and $1');
  }
  /**
   * The board side of square c nearest a point, if it still wants edging and the point is within
   * [reach] of it: the side's line, where along it the point is, and how far in.
   */
  function openSide(c, x, z, reach) {
    let best = null;
    for (const [di, dj] of boardsOf(c)) {
      const bit = sideBit(di, dj);
      if (c.edged & bit) continue;
      // the board: a metre long, starting at (bx, bz), running (ax, az); the slab is (inX, inZ) of it
      const bx = gx(c.i) + (di > 0 ? 1 : 0), bz = gz(c.j) + (dj > 0 ? 1 : 0);
      const ax = di ? 0 : 1, az = di ? 1 : 0, inX = -di, inZ = -dj;
      const along = (x - bx) * ax + (z - bz) * az, off = (x - bx) * inX + (z - bz) * inZ;
      if (along < -0.25 || along > 1.25 || off > reach) continue;
      if (!best || off < best.off) best = { c, bit, bx, bz, ax, az, inX, inZ, along: clamp(along, 0.12, 0.88), off };
    }
    return best;
  }
  /** A metre of edge closed, by whoever: counted, and a late one noted. */
  function edgeDone(c, bit) {
    if (c.edged & bit) return;
    c.edged |= bit;
    surfDirty = true;
    if (netRemote) return;
    const H = cellH(c);
    if (H > 85) {
      gs.edgeLate++;
      toastOnce('edgelate', 'That edge was too hard to close properly. It\'s done. It isn\'t pretty.', 'warn', 40000);
    }
    const left = edgeLeft();
    if (!left) toast(`Every metre of edge done.${site.edges.length > gs.edgesDone ? ` The corners${site.pens.length ? ' and collars' : ''} still want the hand trowel.` : ''}`, 'good');
    else if (left % 5 === 0) toastOnce('edgeM' + left, `${left} m of edge to go.`, '', 60000);
  }
  let edgeSpot = null;      // the metre of edge the hand trowel is on, while it is on one
  /** The hand trowel along a board: about a metre every two seconds, kneeling. */
  function edgeTick(e, dt) {
    const c = e.c, H = cellH(c);
    addDirt('handTrowel', dt * 0.03);
    if (H < 15) { toastOnce('edgesoft', 'Too soft. You\'re drawing in it, not troweling it. Give it a bit.', 'warn', 20000); return; }
    c.ep = c.ep || {};
    c.ep[e.bit] = (c.ep[e.bit] || 0) + dt / 1.9;
    if (c.ep[e.bit] >= 1) edgeDone(c, e.bit);
  }
  /** The edge trowel's disc along a board: a steady run past a metre of it closes that metre. */
  function edgeByMachine(disc, dt) {
    for (const c of gs.cells) {
      if (!boardsOf(c).length || !(c.fill > 20 || gs.poured)) continue;
      const ccx = gx(c.i) + 0.5, ccz = gz(c.j) + 0.5;
      if (Math.abs(ccx - disc.x) > 1.3 || Math.abs(ccz - disc.z) > 1.3) continue;
      const e = openSide(c, disc.x, disc.z, disc.r + 0.12);
      if (!e) continue;
      const H = cellH(c);
      if (H < 15 || H > 95) continue;
      c.ep = c.ep || {};
      c.ep[e.bit] = (c.ep[e.bit] || 0) + dt / 0.45;
      if (c.ep[e.bit] >= 1) edgeDone(c, e.bit);
    }
  }
  function spillOver(c) {
    const over = c.fill - (day.thick + FORM_UP);
    if (over <= 0 || !boardsOf(c).length) return;
    c.fill -= over;
    const [di, dj] = pick(boardsOf(c));
    const x = gx(c.i) + 0.5 + di * 0.62, z = gz(c.j) + 0.5 + dj * 0.62;
    // a grey tongue down the outside of the board, and a pool of it in the gravel
    if (chance(0.5)) emit(x, day.thick / 1000 + 0.07, z, di * rnd(0.3, 0.8), rnd(0.2, 0.6), dj * rnd(0.3, 0.8), 0.6, 0x7d7f80, rnd(0.04, 0.07));
    spillAt(gx(c.i) + 0.5 + di * 0.95, gz(c.j) + 0.5 + dj * 0.95, over / 1000, c, di, dj);
  }
  /**
   * Concrete in the gravel: [m3] of it at x, z, on the waste line, in a heap that grows where it
   * lands — outwards, away from board [di, dj] of square [c], never back in under it.
   */
  function spillAt(x, z, m3, c, di, dj) {
    if (m3 <= 0) return;
    if (isGuest()) net.wasteM3 += m3; else gs.waste += m3;
    gs.spilled = (gs.spilled || 0) + m3;
    const key = `${Math.round(x / 0.7)},${Math.round(z / 0.7)}`;
    let blob = spillBlobs.children.find((b) => b.userData.key === key);
    if (!blob && spillBlobs.children.length >= 40) blob = spillBlobs.children.reduce((a, b) => (hyp(b.position.x, b.position.z, x, z) < hyp(a.position.x, a.position.z, x, z) ? b : a));
    if (!blob) {
      // a low heap, not a painted circle: it stands up off the gravel a little as it grows
      blob = new THREE.Mesh(new THREE.SphereGeometry(0.3, 16, 6, 0, Math.PI * 2, 0, Math.PI / 2), spillMat);
      blob.userData = { key, v: 0, sx: di ? 0.8 : dj ? 1.3 : 1, sz: dj ? 0.8 : di ? 1.3 : 1, c, di, dj, x0: x, z0: z };
      blob.position.set(x, 0.005, z);
      blob.receiveShadow = true;
      spillBlobs.add(blob);
    }
    blob.userData.v += m3;
    blobShape(blob);
    const now = performance.now();
    if (now > spillSaid) { spillSaid = now + 20000; me(fresh(L.meSpill), 'warn'); sfx('splash', x, z); }
    if (gs.spilled > 0.12 && !gs.spillCall1) { gs.spillCall1 = true; at(gs.t + 1, () => managerSpill(true)); }
    else if (gs.spilled > 0.45 && !gs.spillCall2) { gs.spillCall2 = true; at(gs.t + 1, () => managerSpill(false)); }
  }
  /** A heap's size from what's in it now: what went over, less what was shovelled back. */
  function blobShape(blob) {
    const u = blob.userData, v = Math.max(0, u.v - (u.back || 0));
    const r = clamp(0.3 + Math.sqrt(v) * 2.8, 0.3, 1.6), h = clamp(0.02 + v * 0.6, 0.015, 0.14);
    blob.scale.set((r / 0.3) * u.sx, h / 0.3, (r / 0.3) * u.sz);
    if (u.c) {
      const bx = gx(u.c.i) + 0.5 + u.di * 0.58, bz = gz(u.c.j) + 0.5 + u.dj * 0.58;
      if (u.di) blob.position.x = u.di > 0 ? Math.max(u.x0, bx + r * u.sx) : Math.min(u.x0, bx - r * u.sx);
      if (u.dj) blob.position.z = u.dj > 0 ? Math.max(u.z0, bz + r * u.sz) : Math.min(u.z0, bz - r * u.sz);
    }
  }
  /** The spill heap in front of you, near enough to dig. */
  function spillInReach() {
    if (!spillBlobs.children.length) return null;
    camera.getWorldDirection(tmpV);
    const o = camera.position;
    if (tmpV.y > -0.05) return null;
    const t = (0.05 - o.y) / tmpV.y, x = o.x + tmpV.x * t, z = o.z + tmpV.z * t;
    if (hyp(x, z, player.x, player.z) > REACH + 0.8) return null;
    let best = null, bd = 9;
    for (const b of spillBlobs.children) {
      const u = b.userData;
      if (u.v - (u.back || 0) < 0.004) continue;
      const d = hyp(b.position.x, b.position.z, x, z) - b.scale.x * 0.3;
      if (d < 0.5 && d < bd) { bd = d; best = b; }
    }
    return best;
  }
  // Fresh, most of it comes back out of the gravel and goes back in the slab — never all of it:
  // the last of it is mixed with stones and stays where it fell, and on the waste line.
  const SPILL_BACK = 0.85;
  function unspillTick(b, dt) {
    const u = b.userData;
    bootsGet(dt * 0.02);
    addDirt('shovel', Math.max(dt * 0.35, dirtOf('shovel') < 0.2 ? 0.2 : 0));
    gs.energy = clamp(gs.energy - dt * 0.5, 0, 100);
    const can = u.v * SPILL_BACK - (u.back || 0);
    if (can <= 0.002) { toastOnce('spillDone', 'That\'s as much as comes out of the gravel. The rest belongs to the landscape now.', '', 20000); return; }
    const m3 = Math.min(can, 0.022 * dt);
    u.back = (u.back || 0) + m3;
    blobShape(b);
    // back over the board it came over, into the lowest square there that has room
    const near = gs.cells.filter((c) => hyp(gx(c.i) + 0.5, gz(c.j) + 0.5, b.position.x, b.position.z) < 3.2 && c.fill < day.thick + FORM_UP - 10);
    const into = near.length ? near.reduce((a, c) => (c.fill < a.fill ? c : a)) : null;
    if (into) {
      into.fill += m3 * 1000;
      addLoad(into, m3 * 1000);
      cellsDirty = true;
      if (isGuest()) net.wasteM3 -= m3; else gs.waste = Math.max(0, gs.waste - m3);
      gs.spilled = Math.max(0, (gs.spilled || 0) - m3);
    } else toastOnce('spillNoRoom', 'No room on the slab next to it: it goes in the skip. Still waste, but the gravel\'s clean.', 'warn', 30000);
    if (u.back >= u.v * SPILL_BACK - 0.002) {
      gs.stats.unspilled = (gs.stats.unspilled || 0) + 1;
      me(fresh(L.meUnspill), 'good');
    }
  }
  function managerSpill(first) {
    const v = (gs.spilled || 0).toFixed(2);
    const rant = fresh(L.mgrSpill).replace(/\{v\}/g, v);
    remember(`${v} m³ of concrete went over the formwork into the gravel. The manager found out before you finished.`);
    sfx('ring');
    if (first) {
      setTimeout(() => sfx('phoneYell'), 1600);
      modal({
        who: 'The manager, on the phone', title: 'Somebody told him.', sound: 'none', voice: 'manager', personal: true,
        text: [fresh(L.mgrOpen), rant, fresh(L.mgrClose)].join(' '),
        choices: [
          { label: 'Keep the hose in the middle', primary: true },
          { label: 'Say the formwork was too low', fn: () => toast('"The formwork is the height it is. YOU are the one who is too low." He hangs up.', 'warn') },
          { label: 'Blame the pump driver', fn: () => toast('The pump driver hears his name, looks up and slowly shakes his head at you.', 'warn') },
        ],
      });
    } else {
      phoneCall('The manager', [fresh(L.mgrOpen), rant, fresh(L.mgrClose)].join(' '), 'manager');
    }
  }
  // Now and then he rings anyway, about anything at all: out loud, not in a box on the screen.
  let rantAt = 0;
  function updateManager() {
    const now = performance.now();
    const live = ['prep', 'pipes', 'pour', 'wash', 'cure'].includes(gs.phase);
    if (!rantAt) rantAt = now + rnd(150, 260) * 1000;
    if (now < rantAt || !live || gs.packing || gs.waitMode === 'van' || gs.fastForward) return;
    if (now < duckUntil + 2000) { rantAt = now + 6000; return; }
    rantAt = now + rnd(200, 380) * 1000;
    phoneCall('The manager', [fresh(L.mgrOpen), fresh(L.mgrRant), fresh(L.mgrClose)].join(' '), 'manager');
    if (chance(0.25)) remember('The manager rang, for no reason in particular. He found one.');
  }

  /** What goes wrong while pouring, whoever is holding the hose: `dt` seconds of it. */
  function pourTrouble(dt) {
    const progress = filledShare();
    if (gs.mixState === 'stiff' && chance(0.025 * dt)) {
      gs.blocked = irnd(1, 5);
      gs.stats.blockages++;
      toast(fresh(L.blocked), 'warn');
      blockMarker();
    }
    const weakAt = site.forms.findIndex((f) => f.run === site.weak);
    if (site.weak && !gs.blowoutDone && !gs.blowout && progress > 0.35 && weakAt >= 0 && !gs.prep.form[weakAt]) startBlowout();
    // checked or not, now and then a board just gives up
    if (day.surpriseBlowout && !gs.blowoutDone && !gs.blowout && progress > 0.55 && site.forms.length) startBlowout(pick(site.forms).run, true);
    if (day.rebarLift && !gs.rebarLifted && progress > day.rebarLift) rebarUp();
    if (day.batteryDies && gs.prep.laser && !gs.batteryDone && progress > 0.45) {
      gs.batteryDone = true;
      gs.laserBattery = false;
      gs.laserOn = false;
      toast(L.battery, 'warn');
    }
  }
  function blockMarker() {
    const k = gs.blocked;
    const a = k === 0 ? POS.pumpOut : PIPE_ROUTE[k - 1], b = PIPE_ROUTE[k];
    const at2 = day.boom ? P(pump.position.x + 1.5, pump.position.z + 3.2) : P((a.x + b.x) / 2, (a.z + b.z) / 2);
    const m = addMarker('block', at2, day.boom ? 'Hit the boom pipe!' : 'Hit the pipe!', 2.4, () => gs.blocked >= 0, () => {
      gs.blocked = -1;
      sfx('splash');
      toast(fresh(L.unblocked), 'good');
      removeMarker(m);
    }, { w: 2.2, tool: 'hammer' });
  }
  function startBlowout(run, surprise) {
    const r = run || site.weak;
    const side = r.dir === 'x' ? (r.oz < 0 ? 'north' : 'south') : (r.ox < 0 ? 'west' : 'east');
    const s = { name: `${side} side`, p: P(r.mid.x + r.ox * 0.9, r.mid.z + r.oz * 0.9), cells: new Set(r.cells.map((c) => c.j * NX + c.i)) };
    gs.blowout = s;
    toast((surprise ? 'CRACK. You checked it. It doesn\'t care. ' : '') + L.blowout.replace('{side}', s.name), 'warn');
    say('"The formwork! It\'s going!"', 'me');
    sfx('splash', s.p.x, s.p.z); sfx('thud', s.p.x, s.p.z);
    shake = 0.6;
    remember(`The formwork burst on the ${s.name}.`);
    blowoutMarker(s.p);
  }
  function blowoutMarker(p) {
    const m = addMarker('blowout', p, 'Fix the formwork!', 2.8, () => !!gs.blowout, () => {
      gs.blowout = null;
      gs.blowoutDone = true;
      toast('Stakes, a board and a lot of swearing. It holds.', 'good');
      removeMarker(m);
    }, { w: 2.6, tool: 'hammer' });
  }
  /** A bit of mesh rising through the wet concrete: push it back down before it sets there. */
  const liftBars = new THREE.Group();
  scene.add(liftBars);
  function rebarUp() {
    gs.rebarLifted = true;
    const cands = gs.cells.filter((c) => c.fill > day.thick * 0.6 && hyp(gx(c.i) + 0.5, gz(c.j) + 0.5, player.x, player.z) > 2.5);
    if (!cands.length) return;
    const c = pick(cands), x = gx(c.i) + 0.5, z = gz(c.j) + 0.5;
    liftBars.clear();
    liftBars.visible = true;
    for (let k = 0; k < 3; k++) {
      const b = cyl(0.008, 0.008, 0.9, 0x8a4b2a, (k - 1) * 0.15, 0, 0, liftBars, 5);
      b.rotation.z = Math.PI / 2 - 0.5;
      b.position.y = 0.12;
    }
    cyl(0.008, 0.008, 0.5, 0x8a4b2a, 0.15, 0.2, 0, liftBars, 5).rotation.x = Math.PI / 2;
    liftBars.position.set(x, surfY(c.fill), z);
    liftBars.rotation.y = rnd(0, 6);
    const line = fresh(L.rebarUp);
    toast(line, 'warn');
    say('"The mesh is coming up!"', 'me');
    sfx('clank', x, z);
    remember('The mesh came up through the pour.');
    const due = gs.t + 9;
    gs.rebarDue = due;
    const m = rebarMarker(x, z, due);
    at(due, () => {
      if (gs.rebarFixed) return;
      removeMarker(m);
      c.defect = true;
      cellsDirty = true;
      toast('Too late: the mesh has set with its elbow sticking out of your slab. The client will find it with a lawnmower.', 'warn');
      remember('The mesh set sticking out of the slab.');
      charge('Grinding off rebar that set sticking out', 60);
    });
  }

  // ------------------------------------------------------------------ the finger
  // For when shouting is too much effort: a hand comes up in front of you with the one finger that
  // says it all, at whoever you're looking at, or at the day in general.
  const flipHand = new THREE.Group();
  (function buildFlipHand() {
    const skin = 0xd9a47e;
    const sleeve = cyl(0.05, 0.055, 0.34, 0xff7a1a, 0, -0.2, 0, flipHand, 10);
    void sleeve;
    box(0.085, 0.09, 0.07, skin, 0, 0.0, 0, flipHand);
    [-1, 1].forEach((sd) => box(0.02, 0.03, 0.05, skin, sd * 0.03, 0.05, 0.02, flipHand));
    capsule(0.013, 0.085, skin, 0.0, 0.1, 0, flipHand);
    capsule(0.012, 0.04, skin, -0.05, 0.02, 0.02, flipHand).rotation.z = 0.9;
    flipHand.visible = false;
    camera.add(flipHand);
  })();
  let flipT = 0, flipReady = 0;
  function flip() {
    const now = performance.now();
    if (now < flipReady || gs.phase === 'title' || gs.phase === 'end' || modalOpen) return;
    flipReady = now + 1500;
    flipT = 1.5;
    gs.stats.flips++;
    const w = walkerInSight(true);
    const thing = w ? '' : thingInSight();
    const ufoNear = odd.some((o) => o.kind === 'ufo');
    setTimeout(() => {
      if (w && w.crewId) { netSend({ t: 'poke', to: w.crewId, kind: 'flip' }); toast(`You give ${w.name} the finger. ${w.name} saw it.`); } else if (w && w.remoteWid) toast(fresh(L.flip.person)[1], 'warn');
      else if (w && w.isHelper) { const l = fresh(L.helper.flipped).replace('{n}', helper.name); gesture(helper.m, chance(0.5) ? 'flip2' : 'flip1'); toast(l); say(l, helper.voice); } else if (w && w.kind === 'driver') {
        const [kind, l] = fresh(w.who === 'pump' ? L.flip.pump : L.flip.mixer);
        gesture(w.who === 'pump' ? pumpGuy : mixGuy, kind, 2.3, w.who === 'pump');
        toast(l); say(l, w.who === 'pump' ? 'pump' : 'truck');
        if (/honk/i.test(l)) { sfx('honk', mixer.position.x, mixer.position.z); setTimeout(() => sfx('honk', mixer.position.x, mixer.position.z), 420); }
        if (/cough/i.test(l)) sfx('splash', pump.position.x, pump.position.z);
      } else if (w && w.kind === 'dog') toast(fresh(w.cat ? L.flip.cat : L.flip.dog));
      else if (w) {
        const [kind, l] = fresh(L.flip.person);
        toast(l, 'warn'); say(l, w.voice);
        const off = !onSlab(w.m.position.x, w.m.position.z);
        // off the slab they stop to do it properly; on it they do it on the move, faster
        if (off) { w.pause = Math.max(w.pause || 0, 2.1); w.pose = kind === 'phone' ? 'phone' : 'look'; } else w.speed *= 1.5;
        if (kind === 'hurry') w.speed *= 1.4;
        else if (kind !== 'phone') gesture(w.m, kind, 2);
      } else if (ufoNear) toast('The saucer flashes every light it has at you. You have started an interstellar incident.', 'warn');
      else if (thing.startsWith('tool:')) toast(fresh(L.flip.things.tool).replace('{t}', TOOLS[thing.slice(5)].name.toLowerCase()));
      else if (L.flip.things[thing]) toast(fresh(L.flip.things[thing]));
      else toast(fresh(L.flip.nothing));
    }, 450);
  }
  function updateFlip(dt) {
    if (flipT <= 0) { flipHand.visible = false; return; }
    flipT -= dt;
    const u = 1.5 - flipT, up = Math.min(1, u / 0.25) * Math.min(1, flipT / 0.3);
    flipHand.visible = true;
    flipHand.position.set(-0.12, -0.62 + up * 0.44, -0.48);
    flipHand.rotation.set(0.15, 0.25, Math.sin(u * 22) * 0.06 * up);
  }

  // ------------------------------------------------------------------ what they do back
  // Flip somebody off and they answer with their hands: a finger, both fingers, hands clutched to
  // the chest. They turn to face you while they do it, so you can see it.
  const gesturing = new Set();
  const GESTURE = { flip1: [-1.75, 0.1], flip2: [-1.75, -1.75], clutch: [-1.25, -1.25], shout: [-2.75, -2.6] };
  function gesture(m, kind, sec, back) {
    if (!m || !m.userData.armR) return;
    sec = sec || 1.9;
    // where the arms were before, to go back to: a new gesture on top of one keeps the first rest
    const was = m.userData.gest;
    m.userData.gest = { kind, t: sec, len: sec, yaw0: was ? was.yaw0 : m.rotation.y, cur: m.rotation.y, back: !!back,
      r0: was ? was.r0 : m.userData.armR.rotation.x, l0: was ? was.l0 : m.userData.armL.rotation.x };
    gesturing.add(m);
  }
  function updateGestures(dt) {
    gesturing.forEach((m) => {
      const u = m.userData, g = u.gest, fR = u.armR.userData.finger, fL = u.armL.userData.finger;
      g.t -= dt;
      if (g.t <= 0 || !m.visible) {
        fR.visible = false; fL.visible = false;
        u.armR.rotation.z = 0; u.armL.rotation.z = 0;
        // arms back down: nobody else moves a driver's arms, so they stayed up in the air
        u.armR.rotation.x = g.r0; u.armL.rotation.x = g.l0;
        if (g.back) m.rotation.y = g.yaw0;
        u.gest = null;
        gesturing.delete(m);
        return;
      }
      const on = Math.min(1, (g.len - g.t) / 0.3) * clamp(g.t / 0.35, 0, 1);
      const face = Math.atan2(player.x - m.position.x, player.z - m.position.z);
      g.cur = turnTo(g.cur, g.t > 0.4 ? face : g.yaw0, 1 - Math.exp(-dt * 9));
      m.rotation.y = g.cur;
      if (u.head) u.head.rotation.y = lerp(u.head.rotation.y, 0, on);
      const [r, l] = GESTURE[g.kind] || [0, 0], shake = Math.sin(toolT * 19) * (g.kind === 'shout' ? 0.22 : 0.06) * on;
      u.armR.rotation.x = lerp(g.r0, r + shake, on);
      u.armL.rotation.x = lerp(g.l0, l - shake, on);
      const hug = g.kind === 'clutch' ? 0.55 : g.kind === 'flip2' ? -0.12 : 0;
      u.armR.rotation.z = -hug * on; u.armL.rotation.z = hug * on;
      const up = (g.kind === 'flip1' || g.kind === 'flip2') && on > 0.45;
      fR.visible = up; fL.visible = up && g.kind === 'flip2';
      fR.rotation.x = -u.armR.rotation.x; fL.rotation.x = -u.armL.rotation.x;
    });
  }

  // ------------------------------------------------------------------ standing about
  // Stand in one spot for eight or ten seconds while the pump driver or the truck driver is on the
  // clock and one of them lets you know, arms in the air — and again every eight or ten seconds
  // after, angrier, until you move. Pouring into one spot counts: that's how towers start.
  const idle = { x: 0, z: 0, t: 0, next: 9, n: 0 };
  function updateIdle(dt) {
    const who = [];
    if (pumpGuy.visible) who.push('pump');
    if (mixGuy.visible) who.push('truck');
    // floating, trowelling, digging, a job at a flag: that's work, wherever you stand to do it
    const working = input.action && /^(marker|level|repair|trowel|shovel|unspill)$/.test(lastCtxKind);
    const busy = gs.waitMode || player.fall > 0 || gs.packing || working;
    // with the hose going it's where the concrete lands that counts: stand still and sweep it
    // about and that's pouring; ten seconds on one spot and that's a tower
    const aim = streamOn ? (target ? [target._hx, target._hz] : pourOut ? [pourOut.x, pourOut.z] : null) : null;
    const sx = aim ? aim[0] : player.x, sz = aim ? aim[1] : player.z;
    if (!who.length || busy || hyp(sx, sz, idle.x, idle.z) > 0.7) {
      idle.x = sx; idle.z = sz; idle.t = 0; idle.n = 0; idle.next = rnd(8, 10);
      return;
    }
    idle.t += dt;
    if (idle.t < idle.next) return;
    idle.t = 0;
    idle.next = rnd(8, 10);
    // the one pumping it has something to say about a hose held in one place
    const hose = streamOn && who.includes('pump');
    const w = hose ? 'pump' : pick(who), m = w === 'pump' ? pumpGuy : mixGuy;
    const line = fresh(idle.n === 0 ? (hose ? L.idle.hose : L.idle[w]) : L.idle.again);
    idle.n++;
    gesture(m, 'shout', 1.8, w === 'pump');
    sfx('shout', m.position.x, m.position.z);
    toast(`${w === 'pump' ? 'Pump driver' : 'Truck driver'}: ${line}`, 'warn');
    say(line, w);
    gs.stats.yelled = (gs.stats.yelled || 0) + 1;
    if (idle.n === 3) remember(`You stood in one spot long enough for the ${w === 'pump' ? 'pump' : 'truck'} driver to yell at you three times running.`);
  }

  // ------------------------------------------------------------------ the helper
  // On a big slab the manager sends somebody. He ties a bit of mesh, floats a bit of concrete,
  // does an edge now and then, mostly stands about, and says what he thinks.
  const helper = { m: null, name: '', voice: null, state: 'off', goal: null, t: 0, job: null, said: 0 };
  function sendHelper() {
    helper.name = fresh(L.helper.names);
    helper.voice = { p: rnd(0.95, 1.15), r: rnd(1.0, 1.12), key: 'helper', g: 'm', name: helper.name, at: () => helper.m && helper.m.position };
    const l = fresh(L.helper.sent).replace('{n}', helper.name);
    modal({
      who: 'The manager, on the phone', title: 'Reinforcements.', voice: 'manager', sound: 'ring', text: l,
      choices: [{ label: 'Great.', primary: true }, { label: 'I work better alone.', fn: () => toast('"Nobody works better alone. You work WORSE alone. He\'s coming."', 'warn') }],
    });
    const m = makePerson({ vest: 0xd4f53c, hat: 'hard', hatColor: 0xf2f0ea, g: 'm', logo: inMixMaster });
    m.traverse((o) => { if (o.isMesh) o.castShadow = true; });
    m.position.set(44, 0, 0);
    scene.add(m);
    helper.m = m;
    helper.state = 'arrive';
    helper.goal = P(site.box.x0 - 2, site.mid.z + rnd(-2, 2));
    helper.t = 0;
    remember(`The manager sent ${helper.name} to help. He helped, in his way.`);
    charge(`Half of ${helper.name}'s kebab`, 8);
  }
  function helperWalk(dt, speed) {
    const p = helper.m.position, u = helper.m.userData, g = helper.goal;
    const dx = g.x - p.x, dz = g.z - p.z, d = Math.hypot(dx, dz);
    if (d < 0.15) { u.legL.rotation.x *= 0.8; u.legR.rotation.x *= 0.8; return true; }
    const v = Math.min(d, speed * dt);
    p.x += (dx / d) * v; p.z += (dz / d) * v;
    helper.m.rotation.y = Math.atan2(dx, dz);
    u.phase += v * 4.5;
    u.legL.rotation.x = Math.sin(u.phase) * 0.5; u.legR.rotation.x = -Math.sin(u.phase) * 0.5;
    p.y = gs.phase === 'pour' && onSlab(p.x, p.z) ? groundY(p.x, p.z) * 0.4 : groundY(p.x, p.z);
    if (gs.poured && onSlab(p.x, p.z) && gs.H < 60 && chance(dt * 1.6)) stamp('boot', p.x, p.z, helper.m.rotation.y + Math.PI);
    if (gs.pourStarted && gs.H < 25 && onSlab(p.x, p.z) && helper.m.userData.bootCrust) helper.m.userData.bootCrust.forEach((b) => { b.visible = true; });
    return false;
  }
  function updateHelper(dt) {
    if (!helper.m || helper.state === 'off') return;
    helper.t += dt;
    const here = helperWalk(dt, helper.state === 'leave' ? 2.2 : 1.5);
    if (helper.state === 'arrive' && here) {
      helper.state = 'idle';
      const l = fresh(L.helper.hello).replace('{n}', helper.name);
      toast(`${helper.name}: ${l}`); say(l, helper.voice);
    }
    if (helper.state === 'leave') { if (here) { scene.remove(helper.m); helper.m = null; helper.state = 'off'; } return; }
    if (helper.state === 'arrive') return;
    // home time: 16:00, or once the slab is hard enough for everybody to go
    if ((gs.t % 1440) > 16 * 60 || gs.H >= 95) {
      helper.state = 'leave';
      helper.goal = P(44, 0);
      const l = fresh(L.helper.bye);
      toast(`${helper.name}: ${l}`); say(l, helper.voice);
      return;
    }
    if (!here) return;
    helper.job = (helper.job || 0) - dt;
    if (helper.job > 0) {
      // at work where he stands
      if (gs.phase === 'pour') {
        const c = cellAt(helper.m.position.x + Math.sin(helper.m.rotation.y), helper.m.position.z + Math.cos(helper.m.rotation.y));
        if (c && c.fill > 10) { const d = day.thick - c.fill; const nb = neighbours(c); const o = d > 0 ? nb.reduce((a, b) => (b.fill > a.fill ? b : a)) : nb.reduce((a, b) => (b.fill < a.fill ? b : a)); const amt = clamp(d, -20 * dt, 20 * dt); c.fill += amt; o.fill -= amt; cellsDirty = true; }
        helper.m.userData.armR.rotation.x = Math.sin(helper.t * 3) * 0.5 - 0.8;
      }
      return;
    }
    // a new job
    helper.job = rnd(6, 12);
    helper.m.userData.armR.rotation.x = 0;
    if (gs.phase === 'prep' || gs.phase === 'pipes') {
      const tie = site.ties.find((t) => !t.done && !gs.pourStarted);
      if (tie && chance(0.6)) {
        helper.goal = P(tie.x - 0.8, tie.z);
        setTimeout(() => { if (!tie.done && !gs.pourStarted) { tie.done = true; tie.bar.visible = false; tie.twist.visible = true; toast(`${helper.name} tied a bit of mesh. Badly. But it's tied.`); } }, 7000);
        return;
      }
    }
    if (gs.phase === 'pour') {
      const cs = gs.cells.filter((c) => c.fill > 20 && hyp(gx(c.i) + 0.5, gz(c.j) + 0.5, player.x, player.z) > 2.5);
      if (cs.length) { const c = pick(cs); helper.goal = P(gx(c.i) + 0.5, gz(c.j) - 0.6); return; }
    }
    if (gs.phase === 'cure' && gs.H >= 30 && gs.H < 85 && chance(0.3)) {
      const e = site.edges.find((x) => !x.done);
      if (e) {
        helper.goal = P(e.x, e.z);
        setTimeout(() => { if (!e.done && gs.phase === 'cure') { e.done = true; gs.edgesDone++; if (chance(0.35)) { gs.edgeNotes.push('helper'); toast(`${helper.name} did the ${e.label.toLowerCase()}. It's… done. That's the most anyone can say.`, 'warn'); } else toast(`${helper.name} did the ${e.label.toLowerCase()}. Not bad, actually. Don't tell him.`); } }, 9000);
        return;
      }
      // no corners left: a few metres along the boards, round wherever he kneels down
      const open = gs.cells.filter((x) => boardsOf(x).some(([di, dj]) => !(x.edged & sideBit(di, dj))));
      if (open.length) {
        const c0 = pick(open);
        helper.goal = P(gx(c0.i) + 0.5, gz(c0.j) + 0.5);
        setTimeout(() => {
          if (gs.phase !== 'cure') return;
          let n = 0;
          for (const c of open) {
            if (Math.abs(c.i - c0.i) + Math.abs(c.j - c0.j) > 2) continue;
            for (const [di, dj] of boardsOf(c)) { const b = sideBit(di, dj); if (!(c.edged & b)) { edgeDone(c, b); n++; } }
          }
          if (n) toast(`${helper.name} edged ${n} m along the boards. ${chance(0.4) ? 'Wavy, but edged.' : 'Dead straight. Suspicious.'}`);
        }, 9000);
        return;
      }
    }
    if (gs.poured && gs.H < 55 && chance(0.12)) {
      // straight across the slab, to ask you something
      helper.goal = P(player.x + rnd(-1, 1), player.z + rnd(-1, 1));
      toast(fresh(L.helper.oops).replace(/\{n\}/g, helper.name), 'warn');
      return;
    }
    // stand about near the van, on his phone
    const v = vanPoint(rnd(-8, -6), rnd(-3, 3));
    helper.goal = v;
    helper.job = rnd(15, 30);
  }

  function rebarMarker(x, z, due) {
    const m = addMarker('rebarUp', P(x, z), 'Push the mesh down!', 1.6, () => !gs.rebarFixed && gs.t < due, () => {
      gs.rebarFixed = true;
      liftBars.visible = false;
      sfx('wet', x, z);
      toast('Pushed down, stood on, sworn at. It stays. For now.', 'good');
      removeMarker(m);
    }, { w: 2.4 });
    return m;
  }

  // ------------------------------------------------------------------ the pump driver lends a hand
  // On a line-pump day, when the truck has been waiting on you long enough, the pump driver comes
  // over, takes the hose and pours it himself. Fast. Everywhere. You level up after him.
  const helpPour = { on: false, t: 0, spot: null, home: null, next: 0 };
  function maybePumpHelp() {
    if (!day.pumpHelps || day.boom || helpPour.on || gs.pumpHelped || gs.phase !== 'pour') return;
    if (!gs.truck || gs.truck.waiting || gs.truck.left < 1 || gs.blocked >= 0) return;
    const share = filledShare();
    if (gs.pourMins < 18 || share > 0.7 || share < 0.12) return;
    gs.pumpHelped = true;
    const line = fresh(L.pumpHelpStart);
    modal({
      who: 'Pump driver', title: 'He\'s coming over.', voice: 'pump', sound: 'voice',
      text: `${line}\n\nHe walks off the pump, takes the hose out of your hands and wades into your slab with it.`,
      choices: [
        { label: 'Fine. Go on.', primary: true, fn: startPumpHelp },
        { label: 'Absolutely not.', fn: () => { toast('"Too late." He already has it.', 'warn'); startPumpHelp(); } },
      ],
    });
  }
  function startPumpHelp() {
    if (gs.tool === 'hose') gs.tool = 'hands';
    gs.tools.hose.in = 'pumpman';
    helpPour.on = true;
    helpPour.t = 0;
    helpPour.home = P(pumpGuy.position.x, pumpGuy.position.z);
    helpPour.spot = P(SLAB.x1 - 1, gz(ENTRY.j) + 0.5);
    helpPour.next = 0;
    remember('The pump driver took the hose off you and poured it himself. Everywhere.');
  }
  function updatePumpHelp(dt) {
    if (!helpPour.on) return;
    helpPour.t += dt;
    const u = pumpGuy.userData, p = pumpGuy.position;
    const done = helpPour.t > 38 || !gs.truck || gs.truck.left <= 0 || gs.phase !== 'pour';
    const goal = done ? helpPour.home : helpPour.spot;
    const dx = goal.x - p.x, dz = goal.z - p.z, d = Math.hypot(dx, dz);
    if (d > 0.15) {
      const v = Math.min(d, 1.7 * dt);
      p.x += (dx / d) * v; p.z += (dz / d) * v;
      pumpGuy.rotation.y = Math.atan2(dx, dz);
      u.phase += v * 4.5;
      u.legL.rotation.x = Math.sin(u.phase) * 0.5; u.legR.rotation.x = -Math.sin(u.phase) * 0.5;
    } else if (done) {
      helpPour.on = false;
      gs.tools.hose = { in: 'ground', x: SLAB.x1 - 0.8, z: gz(ENTRY.j) + 0.5, yaw: Math.PI / 2 };
      pumpGuy.rotation.y = -Math.PI / 2 - 0.4;
      const l = fresh(L.pumpHelpEnd);
      toast(`Pump driver: ${l} The hose is back at the edge. So are the hills he left.`, 'warn');
      say(l, 'pump');
      return;
    }
    p.y = groundY(p.x, p.z) * 0.5;
    if (done) return;
    // he pours where he stands, a bit too much, and moves on when he feels like it
    u.armR.rotation.x = -1.2; u.armL.rotation.x = -1.0;
    helpPour.next -= dt;
    if (helpPour.next <= 0) {
      helpPour.next = rnd(2.5, 4.5);
      const near = gs.cells.filter((c) => hyp(gx(c.i) + 0.5, gz(c.j) + 0.5, SLAB.x1, gz(ENTRY.j)) < 9 && c.fill < day.thick + 20);
      if (near.length) { const c = pick(near); helpPour.spot = P(gx(c.i) + rnd(0.2, 0.8), gz(c.j) + rnd(0.2, 0.8)); }
    }
    if (d < 1.2) {
      const aim = P(p.x + Math.sin(pumpGuy.rotation.y) * 1.1, p.z + Math.cos(pumpGuy.rotation.y) * 1.1);
      const c = cellAt(aim.x, aim.z) || cellAt(p.x, p.z);
      // a hill of his own making, then off to start another one
      if (c && c.fill > day.thick + (helpPour.hill || (helpPour.hill = rnd(12, 30)))) { helpPour.next = 0; helpPour.hill = 0; }
      else if (c) {
        const add = 150 * dt;
        c.fill += add * 0.85;
        neighbours(c).forEach((n) => { n.fill += (add * 0.15) / 4; });
        gs.truck.left -= add / 1000;
        gs.pouredM3 += add / 1000;
        cellsDirty = true;
        if (chance(dt * 8)) emit(aim.x, 0.9, aim.z, rnd(-0.4, 0.4), rnd(0, 0.5), rnd(-0.4, 0.4), 0.8, 0x7d7f80, 0.08, 1.2);
        if (chance(dt * 3)) paintPour(aim.x + rnd(-0.4, 0.4), aim.z + rnd(-0.4, 0.4));
        if (chance(dt * 0.6)) sfx('splash', aim.x, aim.z);
      }
    }
  }

  function levelTick(c, dt) {
    addDirt('float', dt * 0.06);
    if (chance(dt * 0.004) && breakHandTool('float', fresh(L.floatSnaps), 45)) return;
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
    const Hc = cellH(c);
    addDirt(gs.tool, dt * (Hc < 70 ? 0.04 : 0.008));
    const limit = hand ? 70 : 50;
    paintT -= dt;
    if (Hc < limit && paintT <= 0) {
      paintT = 0.08;
      if (hand) paintBlade(target._hx, target._hz, 0.16); else paintFloat(floatTool.position.x, floatTool.position.z, player.yaw);
    }
    if (!c.marks.length || c.defect) return;
    if (Hc >= limit) {
      if (input.actionTapped) toastOnce('toohard' + gs.tool, hand ? 'Too hard even for the hand trowel. The machine might, until about 80%.' : 'Too hard for the float now. The hand trowel or the machine can still do it, until 70–80%.', 'warn', 30000);
      return;
    }
    if (wearMarks(c, (hand ? 0.7 : 1.1) * dt)) toastOnce('floatout', 'Floated out. Nobody will ever know. Except you. Forever.', 'good', 40000);
  }

  /** Too much in one place: dig it out and throw it where it's low, or over the formwork if nothing is. */
  function shovelTick(c, dt) {
    // one go in wet concrete and it's on the blade
    addDirt('shovel', Math.max(dt * 0.35, dirtOf('shovel') < 0.2 ? 0.2 : 0));
    gs.energy = clamp(gs.energy - dt * 0.4, 0, 100);
    // a real shovelful: hold it too long and you've dug a hole where the high spot was
    const amt = Math.min(160 * dt, c.fill - 15);
    if (amt <= 0) return;
    let low = null;
    for (const o of gs.cells) {
      if (o === c || hyp(gx(o.i) + 0.5, gz(o.j) + 0.5, gx(c.i) + 0.5, gz(c.j) + 0.5) > 3.2) continue;
      if (o.fill < day.thick - 1 && o.fill < c.fill - amt && (!low || o.fill < low.fill)) low = o;
    }
    c.fill -= amt;
    if (low) low.fill += amt;
    else { if (isGuest()) net.wasteM3 += amt / 1000; else gs.waste += amt / 1000; toastOnce('overboard', 'Nowhere low nearby, so over the formwork it goes. Waste, but level waste.', 'warn', 30000); }
    cellsDirty = true;
    paintT -= dt;
    if (paintT <= 0) {
      paintT = 0.25;
      const tx = low ? gx(low.i) + 0.5 : c._hx + rnd(-2, 2), tz = low ? gz(low.j) + 0.5 : c._hz + rnd(-2, 2);
      for (let k = 0; k < 6; k++) emit(c._hx, surfY(c.fill) + 0.1, c._hz, (tx - c._hx) * 1.1 + rnd(-0.3, 0.3), rnd(2.2, 3), (tz - c._hz) * 1.1 + rnd(-0.3, 0.3), 0.8, 0x7d7f80, 0.07, 1);
      sfx('wet', c._hx, c._hz);
      if (low) paintPour(tx, tz);
    }
    toastOnce('shovel', fresh(L.shovelLines), '', 45000);
    if (c.fill < day.thick - 18 && performance.now() > (shovelTick.said || 0)) { shovelTick.said = performance.now() + 15000; me(fresh(L.meDig), 'warn'); }
    if (chance(dt * 0.003)) breakHandTool('shovel', 'The shovel handle snaps with a crack like a starting pistol. You are now holding a stick and a grudge.', 25);
  }

  // the power trowels
  const mpos = { x: 0, z: 0, on: false };   // the middle of the machine in hand, set every frame
  /** 0 when the concrete carries the machine, up to 1 when it is still soft enough to dig into. */
  function digFactor(Hat) {
    const H = Hat === undefined ? (mpos.on ? hAt(mpos.x, mpos.z) : gs.H) : Hat, heavy = gs.tool === 'rideOn' ? 5 : 0;
    return fitted() === 'blades' ? clamp((50 - H) / 20, 0, 1) : clamp((25 + heavy - H) / 15, 0, 1);
  }
  let dugWarn = 0;
  function trowelTick(dt) {
    const id = gs.tool, pans = fitted() === 'pans', H = mpos.on ? hAt(mpos.x, mpos.z) : gs.H;
    if (gs.broken[id]) { toastOnce('dead' + id, `The ${TOOLS[id].name.toLowerCase()} is dead. A spare is on its way from the yard.`, 'warn', 30000); return; }
    gs.runSecs[id] = (gs.runSecs[id] || 0) + dt;
    if (day.breakTool === id && gs.runSecs[id] > day.breakAfter && !gs.broken[id + 'Died']) { gs.broken[id + 'Died'] = true; breakMachine(id); return; }
    if (H >= 8) addDirt(id, dt * (H < 85 ? 0.012 : 0.003));
    if (H < 8) { if (input.actionTapped) toast(pans ? L.tooSoftMachine : L.tooSoftBlades, 'warn'); return; }
    if (input.actionTapped && pans && gs.panPasses.length >= 3) toastOnce('pans3', 'Three pan passes is plenty. Now you\'re just polishing the pans.', '', 60000);
    if (input.actionTapped && !pans && gs.bladePasses.length >= 3) toastOnce('blades3', 'Three blade passes. It shines like a bowling alley. You can stop.', '', 60000);
    const key = passKey();
    const dig = digFactor(H);
    const ds = discs();
    let covered = false, digAny = 0;
    for (const disc of ds) {
      const R = disc.r;
      // the small one is the edge trowel: run along a board, it closes the edge as it goes
      if (id === 'trowelSmall') edgeByMachine(disc, dt);
      for (const c of gs.cells) {
        // mid-pour, only what has been poured: the rest is mesh and stones
        if (!gs.poured && c.fill <= 20) continue;
        const ccx = SLAB.x0 + c.i + 0.5, ccz = SLAB.z0 + c.j + 0.5;
        const d = hyp(ccx, ccz, disc.x, disc.z);
        if (d > 1.4) continue;
        // each square as hard as its own load: the machine digs into one and glides over the next
        const Hc = cellH(c), dig = digFactor(Hc);
        digAny = Math.max(digAny, dig);
        // how much the surface still gives: everything at 50%, nothing at 80%. The pan wipes prints
        // 60% faster than the blades: one steady pass takes a fresh boot print out with the pan.
        const give = clamp((80 - Hc) / 30, 0, 1);
        const wear = (pans ? 1.6 : 1.0) * give * dt;
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
        // each square remembers how hard it was when the pass went over it: that's what the pass is judged on
        if (d < R + 0.35 && !c[key]) { c[key] = true; c[key + 'H'] = Hc; covered = true; surfDirty = true; }
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
    if (digAny > 0 && dig <= 0) {
      if (performance.now() > dugWarn) { dugWarn = performance.now() + 20000; toast('Soft patch: a later load, still behind the rest. The machine dips into it.', 'warn'); }
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
    if (covered && passCoverage(key) >= 0.9 && !isGuest()) {
      if (pans && gs.panPasses.length >= 3) { gs.cells.forEach((c) => { c.covP = false; }); return; }
      if (!pans && gs.bladePasses.length >= 3) { gs.cells.forEach((c) => { c.covB = false; }); return; }
      completePass(pans ? 'pan' : 'blade');
    }
  }
  function thumb(c) {
    if (!gs.poured && !mixOf(c)) { toast('It\'s still a building site, not a slab. Nothing to test.'); return; }
    const Hc = cellH(c), l = mixOf(c);
    const line = L.thumb.find(([h]) => Hc < h)[1];
    sfx(Hc < 60 ? 'soft' : 'hard');
    toast(`${Math.floor(Hc)}%${l && gs.loads.filter(Boolean).length > 1 ? ` (truck ${l.no})` : ''} · ${line}`);
    if (Hc < 30) stamp('thumb', SLAB.x0 + c.i + 0.5 + rnd(-0.3, 0.3), SLAB.z0 + c.j + 0.5 + rnd(-0.3, 0.3), 0);
  }

  // ------------------------------------------------------------------ controls
  const touchLayer = $('#touch');
  const joy = $('#joy'), joyKnob = $('#joyKnob');
  // the player's own feel for it: how far the thumb goes for full speed, how fast the view turns
  let walkSens = storedNum('pourday.sensWalk', 1), lookSens = storedNum('pourday.sensLook', 1);
  let invertY = store('pourday.invertY') === 'on';
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
      const len = Math.hypot(dx, dy), max = 52 / walkSens;
      if (len > max) { dx = (dx / len) * max; dy = (dy / len) * max; }
      input.jx = dx / max; input.jy = dy / max;
      joyKnob.style.transform = `translate(${dx}px, ${dy}px)`;
    } else if (e.pointerId === lookId) {
      const sens = (e.pointerType === 'mouse' ? 0.0045 : 0.0058) * lookSens;
      player.yaw -= (e.clientX - lookX) * sens;
      player.pitch = clamp(player.pitch - (e.clientY - lookY) * sens * (invertY ? -1 : 1), -1.35, 1.1);
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
    if (lvl.on) {
      lvl.bx += (e.clientX - actX) * 0.006;
      lvl.by += (e.clientY - actY) * 0.006;
      actX = e.clientX; actY = e.clientY;
      return;
    }
    const sens = (e.pointerType === 'mouse' ? 0.0045 : 0.0058) * lookSens;
    player.yaw -= (e.clientX - actX) * sens;
    player.pitch = clamp(player.pitch - (e.clientY - actY) * sens * (invertY ? -1 : 1), -1.35, 1.1);
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
    else toastOnce('hands', 'Your hands are empty. Look at a tool — on the van\'s ramp, or wherever you left it — and press Pick up.', '', 20000);
  }
  $('#btnTool').addEventListener('click', () => toolButton());
  $('#btnLaser').addEventListener('click', () => altButton());
  $('#btnWait').addEventListener('click', () => waitMenu());
  $('#btnCoffee').addEventListener('click', () => coffee());
  $('#btnFlip').addEventListener('click', () => flip());
  $('#btnFinish').addEventListener('click', () => {
    if (isGuest()) { netSend({ t: 'finish' }); toast(`You tell ${net.hostName} the pour is done.`); return; }
    modal({
      personal: true,
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
    gs.needs.wee += 22;
    gs.stats.coffee++;
    gs.energy = clamp(gs.energy + 15, 0, 100);
    // takes the edge off last night, a little
    if (gs.hangover) gs.hangStart -= 25;
    simulate(2, awayNow());
    cupT = 1.6;
    sfx('slurp');
    toast(`${fresh(L.coffee)} (${gs.cups} left)`);
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
    if (isGuest()) { toast(`Only ${net.hostName} can make time fly. Ask nicely. Or shout.`); return; }
    if (gs.waitMode || gs.fastForward) {
      if (gs.waitMode === 'van') toast(fresh(L.vanNap));
      gs.waitMode = null; gs.fastForward = null; showWait();
      return;
    }
    const choices = [];
    if ((gs.phase === 'prep' || gs.phase === 'pipes') && !gs.pumpHere) choices.push({ label: `Wait for the pump (due ${clock(gs.pumpAt)})`, primary: true, fn: () => { gs.fastForward = 'pump'; showWait(); } });
    if ((gs.phase === 'pipes' || gs.phase === 'pour') && gs.pipes === PIPE_N && !gs.truck && gs.nextTruckAt > gs.t) choices.push({ label: `Wait for the truck (due ${clock(gs.nextTruckAt)})`, primary: true, fn: () => { gs.fastForward = 'truck'; showWait(); } });
    if (gs.phase === 'cure' || gs.phase === 'wash') {
      if (hyp(player.x, player.z, site.mid.x, site.mid.z) < 16) choices.push({ label: 'Stand guard by the slab', primary: true, fn: () => { gs.waitMode = 'guard'; showWait(); } });
      choices.push({ label: 'Nap in the van (fastest, but nobody guards the slab)', fn: () => {
        gs.waitMode = 'van'; showWait(); vanFloor();
        // behind the wheel, looking out at the slab you should be guarding
        player.yaw = van.rotation.y - Math.PI / 2; player.pitch = -0.1;
      } });
    }
    if (!choices.length) { toast('Nothing to wait for. There is always something to do. That\'s the job.'); return; }
    choices.push({ label: 'Never mind' });
    modal({ personal: true, who: 'Wait', title: 'Let time do its thing.', text: gs.poured ? `Hardness ${Math.floor(gs.H)}%. Pans in ${dur(etaTo(25))}, blades in ${dur(etaTo(55))}, 95% in ${dur(etaTo(95))}.` : '', choices });
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
    // out of the van by the driver's door, whatever it was that got you out
    if (inVanWas && gs.waitMode !== 'van') { const q = vanPoint(2.3, -2.1); player.x = q.x; player.z = q.z; }
    inVanWas = gs.waitMode === 'van';
    if (gs.waitMode === 'van') { const q = vanPoint(1.3, -0.45); player.x = q.x; player.z = q.z; player.moving = false; return; }
    if (gs.packing) { player.moving = false; return; }
    if (player.fall > 0 || performance.now() < gs.stuckUntil || gs.fitting || retch.t > 0 || cramp.t > 0) { player.moving = false; return; }
    const c = cellAt(player.x, player.z);
    const wet = gs.phase === 'pour' && c && c.fill > 20;
    const tool = gs.tool, running = input.action && lastCtxKind === 'trowel';
    let speed = 4.2;
    if (wet) speed = 1.7;
    if (isMachine(tool)) speed = TOOLS[tool].ride ? (running ? 1.8 : 2.6) : (running ? 1.4 : 2.2);
    if (gs.carrying) speed *= 0.7;
    if (gs.needs.poo > 85) speed *= 0.75;
    // thumb all the way over: a run, while there's energy for one and nothing heavy in your hands
    const canRun = len > 0.92 && !wet && !isMachine(tool) && !gs.carrying && gs.energy > 12 && gs.needs.poo <= 85 && !(gs.accident && gs.accident.kind === 'shit');
    player.run = canRun ? Math.min(1, (player.run || 0) + dt * 2.5) : Math.max(0, (player.run || 0) - dt * 4);
    if (player.run > 0) {
      speed *= 1 + 0.6 * player.run;
      gs.energy = clamp(gs.energy - player.run * dt * 0.3, 0, 100);
      if (player.run >= 1) toastOnce('run', 'Running. On a building site. Your mother would be so proud.', '', 600000);
    }
    if (gs.energy < 20) speed *= 0.8;
    // a kilo of concrete on each boot slows anybody down
    speed *= 1 - 0.22 * (gs.boots || 0);
    // walking carefully, in the state you're in
    if (gs.accident && gs.accident.kind === 'shit' && !gs.accident.washed) speed *= 0.8;
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    const rx = Math.cos(player.yaw), rz = -Math.sin(player.yaw);
    let vx = (fx * -my + rx * mx) * speed, vz = (fz * -my + rz * mx) * speed;
    // last night steers too: a slow drift to one side and back while you walk
    const hang = hangoverNow();
    if (hang > 0 && len > 0.08) {
      const drift = Math.sin(performance.now() / 1000 * 0.9) * 1.1 * hang * len;
      vx += rx * drift; vz += rz * drift;
    }
    let [nx, nz] = collide(player.x + vx * dt, player.z + vz * dt);
    // what you are holding decides where you can go: the hose is only so long, a walk-behind
    // machine is in front of you and does not go through pipes, the ride-on is the size it is
    const from = day.boom ? P(pump.position.x + TURRET.x, pump.position.z) : pipeEnd();
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
    // on the ride-on you're sitting: your boots are on its footplate, not in the slab, and
    // whatever the concrete does, it doesn't put you on your backside
    const seated = gs.tool === 'rideOn';
    // wet concrete comes away on your boots: more of a soup, less of a stiff mix
    if (now && moved > 0 && !seated && gs.pourStarted && now.fill > 20 && cellH(now) < 25) bootsGet(moved * (gs.mixState === 'soup' ? 0.1 : gs.mixState === 'stiff' ? 0.05 : 0.075));
    // Steps come by the clock, not by the metre: counted by distance, a jog came out at nine a
    // second, which is a sewing machine, not a man. Feet do two a second walking, under three running.
    const v = moved / Math.max(dt, 0.001);
    if (v > 0.4) stepSnd += dt * clamp(1.3 + v * 0.22, 1.5, 2.9);
    if (stepSnd >= 1) {
      stepSnd = 0;
      // off the slab with concrete on your boots: grey prints across the gravel, and some of it stays there
      if (!now && gs.boots > 0.12 && !inVanNow()) { trackPrint(player.x, player.z, player.yaw); gs.boots = Math.max(gs.bootsFloor || 0, gs.boots - 0.008); }
      if (gs.accident && gs.accident.kind === 'shit' && !gs.accident.washed) sfx('squelch');
      if (!now || (!gs.pourStarted && now.fill < 3)) sfx('gravel');
      else if (gs.phase === 'pour' && now.fill > 20) sfx('wet');
      else if (gs.poured && gs.H < 55) sfx('soft');
      else sfx('hard');
    }
    if (now && moved > 0 && !seated && !gs.pourStarted && (gs.phase === 'prep' || gs.phase === 'pipes' || gs.phase === 'pour')
      && performance.now() > tripAt && chance((player.run > 0.5 ? 0.004 : 0.0016) * (gs.hangover ? 2 : 1))) trip();
    if (now && moved > 0) {
      player.stepAcc += moved;
      if (gs.phase === 'pour' && gs.pourStarted && now.fill > 30 && !seated) {
        if (!gs.fellInPour && filledShare() > 0.2 && chance(0.0015)) { gs.fellInPour = true; fall(); }
        else if (chance(0.0025) && performance.now() > stuckMsg) {
          gs.stuckUntil = performance.now() + 2600; stuckMsg = performance.now() + 45000;
          bootsGet(0.25);
          sfx('wet');
          me(fresh(L.meStuck));
          if (chance(0.3)) remember(fresh(L.stuck));
        }
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
      if (machineTool() && input.action && chance(0.006 * digFactor())) {
        // the ride-on bucks and you hang on; a walk-behind twists out of your hands and you go down
        if (seated) { shake = Math.max(shake, 0.25); sfx('thunk'); toastOnce('rodeo', 'The ride-on digs in and bucks like a rodeo bull. You hang on. The slab doesn\'t: it\'s too soft for it yet.', 'warn', 30000); }
        else fall('The machine digs in, twists, and throws you on your butt. Told you it was early.');
      }
      if (gs.energy < 18 && !seated && chance(0.003)) fall('Your legs file for early retirement. Down you go.');
    }
  }

  function updateTarget() {
    camera.getWorldDirection(tmpV);
    const o = camera.getWorldPosition(tmpV2);
    target = null;
    pourOut = null;
    if (tmpV.y < -0.02) {
      const yPlane = gs.pourStarted ? surfY(day.thick) : 0.02;
      const t = (yPlane - o.y) / tmpV.y;
      const x = o.x + tmpV.x * t, z = o.z + tmpV.z * t;
      if (onSlab(x, z) && hyp(x, z, player.x, player.z) < REACH) { target = cellAt(x, z); target._hx = x; target._hz = z; }
      // with the hose, past the boards is somewhere too: into the gravel, and onto the waste line
      // (only off the slab: a spot on it just out of reach is out of reach, not over the boards)
      else if (gs.tool === 'hose' && gs.phase === 'pour' && !onSlab(x, z) && hyp(x, z, player.x, player.z) < REACH + 0.6) {
        pourOut = pastTheBoards(x, z);
        // a hand's breadth past the boards is the edge square, not the gravel: nobody means that.
        // Pour clearly out into the gravel and that's where it goes.
        if (pourOut && pourOut.d < 0.4) {
          const c = pourOut.c;
          target = c; target._hx = clamp(x, gx(c.i) + 0.2, gx(c.i) + 0.8); target._hz = clamp(z, gz(c.j) + 0.2, gz(c.j) + 0.8);
          pourOut = null;
        }
      }
      // edging, you look at the board itself: that is the concrete right against it
      else if (gs.tool === 'handTrowel' && !onSlab(x, z) && hyp(x, z, player.x, player.z) < REACH + 0.6) {
        const o = pastTheBoards(x, z);
        if (o && o.d < 0.45) { const c = o.c; target = c; target._hx = clamp(x, gx(c.i) + 0.05, gx(c.i) + 0.95); target._hz = clamp(z, gz(c.j) + 0.05, gz(c.j) + 0.95); }
      }
    }
    nearMarker = null;
    let best = 2.0;
    for (const m of markers) {
      if (!m.active()) continue;
      const d = hyp(m.x, m.z, player.x, player.z);
      if (d < best) { best = d; nearMarker = m; }
    }
    nearTool = null;
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
        if (a < Math.atan((isMachine(id) ? 0.5 : 0.4) / d3) + 0.24 && a < bestA) { bestA = a; nearTool = id; }
      }
    }
  }

  const lvl = { bx: 0, by: 0, ok: 0, on: false, beep: 0, started: false };
  function bubbleTick(dt) {
    const el = $('#level');
    if (!lvl.started) { const a = rnd(0, Math.PI * 2), r = rnd(0.55, 0.9); lvl.bx = Math.cos(a) * r; lvl.by = Math.sin(a) * r; lvl.started = true; }
    lvl.on = true;
    el.hidden = false;
    // the head settles a little by itself, and the tripod creeps; your thumb does the rest
    lvl.bx += (rnd(-1, 1) * 0.5 - lvl.bx * 0.15) * dt;
    lvl.by += (rnd(-1, 1) * 0.5 - lvl.by * 0.15) * dt;
    const r = Math.hypot(lvl.bx, lvl.by);
    if (r > 1) { lvl.bx /= r; lvl.by /= r; }
    const inside = r < 0.2;
    lvl.ok = inside ? lvl.ok + dt : Math.max(0, lvl.ok - dt * 2);
    lvl.beep -= dt;
    if (inside && lvl.beep <= 0) { lvl.beep = 0.28; sfx('rx'); }
    $('#bubble').style.transform = `translate(${(lvl.bx * 38).toFixed(1)}px, ${(lvl.by * 38).toFixed(1)}px)`;
    $('#bubble').classList.toggle('in', inside);
    $('#levelFill').style.width = `${Math.min(100, (lvl.ok / 1.4) * 100)}%`;
    if (lvl.ok >= 1.4 && nearMarker && nearMarker.id === 'laserLevel') {
      const m = nearMarker;
      lvl.on = false; el.hidden = true;
      if (m.done() !== false) netMarker(m.id);
      if (!m.active()) m.group.visible = false;
    }
  }
  function levelOff() { if (lvl.on) { lvl.on = false; $('#level').hidden = true; } }
  let lastCtxKind = '';
  const btnState = { idle: null, label: null, fill: null, tool: null, alt: null, wait: null };
  const btnTool = $('#btnTool'), btnAlt = $('#btnLaser');
  const actLabel = $('#actLabel'), actFill = $('#actFill'), actIco = $('#actIco');
  const ACT_ICON = { pour: 'hose', level: 'float', repair: 'trowel', edge: 'trowel', trowel: 'machine', shovel: 'shovel', unspill: 'shovel', thumb: 'hand', shout: 'speak', marker: 'flag', none: 'hand' };
  function updateAction(dt) {
    if (gs.packing) { input.action = false; return; }
    updateTarget();
    const ctx = context();
    if (!ctx || ctx.kind !== lastCtxKind) holdT = 0;
    lastCtxKind = ctx ? ctx.kind : '';
    if (input.action && ctx && ctx.kind === 'marker' && nearMarker && nearMarker.id === 'laserLevel') bubbleTick(dt);
    else levelOff();
    if (input.action && ctx && ctx.kind !== 'none') {
      if (gs.waitMode === 'guard') { gs.waitMode = null; showWait(); }
      doAction(ctx, dt);
    } else {
      holdT = 0;
      if (input.actionTapped && ctx && ctx.kind === 'none' && ctx.label !== '—') toastOnce('idle' + ctx.label, ctx.label + '.', '', 12000);
    }
    streamOn = !!(ctx && ctx.kind === 'pour' && input.action && (target || pourOut) && gs.truck && gs.truck.left > 0);
    const land = streamOn ? (target ? new THREE.Vector3(target._hx, surfY(target.fill), target._hz) : new THREE.Vector3(pourOut.x, pourOut.inside ? (day.thick + FORM_UP) / 1000 : 0.03, pourOut.z)) : null;
    const mine = gs.tools.hose && gs.tools.hose.in === 'hand' && (!gs.tools.hose.by || gs.tools.hose.by === net.me);
    if (mine && viewTools.hose.visible) {
      const mouth = updateHose(land, dt);
      if (land) drawStream(mouth, land, dt); else hideStream();
    } else { hideHose(); hideStream(); }
    input.actionTapped = false;
    // the end hose, from the last pipe to your hand, or to wherever you left it
    const hose = gs.tools.hose;
    if (hose && !(mine && hoseFlex.visible) && (hose.in === 'hand' || hose.in === 'ground' || hose.in === 'pumpman')) {
      const end = new THREE.Vector3();
      const holder = hose.in === 'hand' && hose.by && hose.by !== net.me ? net.crew.get(hose.by) : null;
      if (holder && holder.m) end.set(holder.x - Math.sin(holder.yaw) * 0.45, 1.0, holder.z - Math.cos(holder.yaw) * 0.45);
      else if (hose.in === 'hand') { end.set(0.36, -0.75, -0.35); camera.localToWorld(end); }
      else if (hose.in === 'pumpman') end.set(pumpGuy.position.x + Math.sin(pumpGuy.rotation.y) * 0.5, pumpGuy.position.y + 1.0, pumpGuy.position.z + Math.cos(pumpGuy.rotation.y) * 0.5);
      else end.set(hose.x, groundY(hose.x, hose.z) + 0.05, hose.z);
      const last = pipeEnd();
      endHose.visible = true;
      stretch(endHose, day.boom ? boomTip : new THREE.Vector3(last.x, 0.15, last.z), end);
    } else endHose.visible = false;
    // HUD button
    const idle = !ctx || ctx.kind === 'none';
    const label = ctx ? (ctx.kind === 'marker' ? `Hold: ${ctx.label}` : ctx.label) : '—';
    const fill = ctx && ctx.kind === 'marker' ? `${Math.min(100, (holdT / ctx.hold) * 100)}%` : '0%';
    if (idle !== btnState.idle) { btnAction.classList.toggle('idle', idle); btnState.idle = idle; }
    const kind = ctx ? ctx.kind : 'none';
    if (kind !== btnState.kind) { btnState.kind = kind; actIco.className = `ico i-${ACT_ICON[kind] || 'hand'}`; }
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
      if (!dog && u.bootCrust && !u.bootCrust[0].visible) { const cw = cellAt(x, z); if (cw && gs.pourStarted && cw.fill > 20 && cellH(cw) < 25) u.bootCrust.forEach((b) => { b.visible = true; }); }
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
      mixGuy.position.set(mixer.position.x - 3.2, 0, mixer.position.z + 1.9);
      mixGuy.rotation.y = -1.9;
    } else mixGuy.visible = false;
  }

  // ------------------------------------------------------------------ chatter
  // Every minute or two, when nobody else is talking: a thought of your own, a text from somebody,
  // the pump's radio, a neighbour at a window, or the drivers. On screen and out loud.
  let chatterAt = 0;
  function updateChatter() {
    const now = performance.now();
    const quiet = gs.phase === 'prep' || gs.phase === 'pipes' || gs.phase === 'pour' || gs.phase === 'wash' || gs.phase === 'cure';
    if (!chatterAt) chatterAt = now + rnd(35, 60) * 1000;
    if (now < chatterAt || !quiet || gs.packing || gs.waitMode === 'van') return;
    // somebody is already talking: wait for them to finish
    if (now < duckUntil + 2500) { chatterAt = now + 4000; return; }
    chatterAt = now + rnd(50, 95) * 1000;
    const kinds = [[3, 'thought'], [2.5, 'text']];
    const radioFrom = pump.visible && gs.pumpHere ? 'The pump\'s radio' : mixer.visible && gs.truck ? 'The mixer\'s radio' : null;
    if (radioFrom) kinds.push([2, 'radio']);
    if ((gs.phase === 'pour' || gs.phase === 'pipes') && gs.pumpHere) kinds.push([2.5, 'crew']);
    const h = (gs.t % 1440) / 60;
    kinds.push([h < 8 || h > 19 ? 1.6 : 0.6, 'neighbour']);
    if (helper.m && helper.state !== 'leave') kinds.push([3, 'helper']);
    const mates = inTeam() ? [...net.crew.values()].filter((c) => c.id !== net.me && c.m) : [];
    if (mates.length) kinds.push([2.5, 'mate']);
    const kind = weighted(kinds);
    if (kind === 'helper') {
      // he gets his favourite line in early
      const l = helper.said++ === 0 ? L.helper.talk[0] : fresh(L.helper.talk);
      toast(`${helper.name}: ${l}`);
      say(l, helper.voice);
      return;
    }
    if (kind === 'mate') {
      const c = pick(mates), l = fresh(L.mateTalk);
      toast(`${c.name}: ${l}`);
      say(l, crewVoice(c.id));
      return;
    }
    if (kind === 'thought') {
      const l = fresh(L.thoughts);
      toast(l);
      say(l, 'me');
    } else if (kind === 'text') {
      const [from, voice, text] = fresh(L.texts);
      phoneText(from, text, voice);
    } else if (kind === 'radio') {
      const l = fresh(L.radio);
      toast(`${radioFrom}: ${l}`);
      say(l, 'radio');
    } else if (kind === 'crew') {
      const l = fresh(L.pourJokes.concat(L.driverTalk, L.mixTalk));
      toast(l);
      if (/^Pump driver/.test(l)) say(l, 'pump');
      else if (/^Mixer driver/.test(l)) say(l, 'truck');
    } else {
      const l = fresh(L.neighbour);
      toast(`A window across the road opens. ${l}`, 'warn');
      say(l, 'neighbour');
    }
  }

  /** The van's back: doors round to the sides, then the ramp out and down. */
  function updateVanBack(dt) {
    const was = vanBack.open;
    vanBack.open = vanBack.want > vanBack.open ? Math.min(vanBack.want, vanBack.open + dt * 0.9) : Math.max(vanBack.want, vanBack.open - dt * 1.4);
    if (was === vanBack.open && was !== 0 && was !== 1) return;
    const d = clamp(vanBack.open * 2, 0, 1), r = clamp(vanBack.open * 2 - 1, 0, 1);
    vanBack.doors.forEach(({ hinge, s }) => { hinge.rotation.y = s * 1.9 * d * d * (3 - 2 * d); });
    vanInside.visible = d > 0.05;
    rampPivot.visible = r > 0.02;
    rampPivot.position.x = lerp(-0.6, RAMP_TOP.x, r);
    rampPivot.rotation.z = RAMP_DOWN * r;
    laserCase.visible = r > 0.9 && gs.laserInVan;
    vanBucket.visible = r > 0.9;
    if (was < 1 && vanBack.open >= 1 && vanBack.onOpen) { const f = vanBack.onOpen; vanBack.onOpen = null; f(); }
  }
  // tools in the air: out of the van, or anywhere else they get thrown
  const flights = [];
  function launchTool(id, from, to, secs) {
    gs.tools[id] = { in: 'flying', x: to.x, z: to.z, yaw: rnd(0, 6) };
    flights.push({ id, from: from.clone(), to: new THREE.Vector3(to.x, groundY(to.x, to.z) + 0.02, to.z), t: 0, secs: secs || 0.8, spin: rnd(6, 12) });
  }
  function updateFlights(dt) {
    for (let k = flights.length - 1; k >= 0; k--) {
      const f = flights[k], g = lying[f.id];
      f.t += dt / f.secs;
      const u = Math.min(1, f.t);
      g.visible = true;
      g.position.set(lerp(f.from.x, f.to.x, u), lerp(f.from.y, f.to.y, u) + 1.1 * 4 * u * (1 - u), lerp(f.from.z, f.to.z, u));
      g.rotation.set(u * f.spin, u * 3, u * f.spin * 0.5);
      if (u >= 1) {
        flights.splice(k, 1);
        g.rotation.set(0, 0, 0);
        gs.tools[f.id].in = 'ground';
        sfx('clank', f.to.x, f.to.z);
        emit(f.to.x, 0.05, f.to.z, 0, 0.6, 0, 0.5, 0x9a8f7c, 0.06, 0.4);
      }
    }
  }

  // Wet concrete doesn't stand up in a tower where the hose is: it slumps out to the squares round
  // it until the step between them is what the mix will hold — hardly any for soup, a good bit for
  // a stiff one. So one spot makes a mound to rake out, and a mound against a board goes over it.
  const SLUMP = { soup: 12, ok: 32, stiff: 55 };
  function flowTick(dt) {
    const step = SLUMP[gs.mixState] || SLUMP.ok, k = Math.min(0.12, 1.5 * dt);
    let moved = false;
    const pair = (a, b, s, w) => {
      const d = a.fill - b.fill;
      if (d <= s && -d <= s) return;
      const hi = d > 0 ? a : b, lo = d > 0 ? b : a;
      if (cellH(hi) > 8) return;           // going off: it stays where it is
      const amt = (Math.abs(d) - s) * k * w;
      hi.fill -= amt; lo.fill += amt;
      carryLoad(hi, lo, amt);
      moved = true;
    };
    for (const c of gs.cells) {
      if (isOn(c.i + 1, c.j)) pair(c, gs.grid[c.idx + 1], step, 1);
      if (isOn(c.i, c.j + 1)) pair(c, gs.grid[c.idx + NX], step, 1);
      if (isOn(c.i + 1, c.j + 1)) pair(c, gs.grid[c.idx + NX + 1], step * 1.41, 0.5);
      if (isOn(c.i - 1, c.j + 1)) pair(c, gs.grid[c.idx + NX - 1], step * 1.41, 0.5);
    }
    if (!moved) return;
    cellsDirty = true;
    for (const c of gs.cells) if (c.fill > day.thick + FORM_UP) spillOver(c);
  }
  /** Concrete that runs from one square to the next takes its truck with it. */
  function carryLoad(from, to, mm) {
    if ((to.covP || to.covB || to.edged) && mm > 0.3) { to.covP = to.covB = false; to.edged = 0; to.ep = null; surfDirty = true; }
    const n = from.load;
    if (!n) return;
    if (!to.load || to.fill - mm < 5) { if (to.load === n) to.loadMm += mm; else { to.load = n; to.loadMm = mm; to.otherMm = 0; } return; }
    if (to.load === n) { to.loadMm += mm; return; }
    to.otherMm += mm;
    if (to.otherMm > to.loadMm) { to.load = n; [to.loadMm, to.otherMm] = [to.otherMm, to.loadMm]; }
  }

  /** Hand tools left lying on the slab while the concrete is fresh sink into it. Machines stand on their pans; they're meant to. */
  function soakTools(dt) {
    if (!gs.pourStarted || gs.H > 60) return;
    for (const id of DIRTY_TOOLS) {
      const tl = gs.tools[id];
      if (!tl || tl.in !== 'ground' || isMachine(id)) continue;
      const c = cellAt(tl.x, tl.z);
      if (!c || c.fill < 15 || cellH(c) > 60) continue;
      addDirt(id, dt * 0.6);
      const the = TOOLS[id].the, are = id === 'pliers';
      if (dirtOf(id) > 0.15) toastOnce('soak' + id, `${the[0].toUpperCase()}${the.slice(1)} ${are ? 'are' : 'is'} lying in the concrete. Of course ${are ? 'they are' : 'it is'}.`, 'warn', 60000);
    }
  }
  function updateWorld(dt) {
    updateBoom(dt);
    updateVanBack(dt);
    updateFlip(dt);
    updateIdle(dt);
    updateMyBoots();
    if (!isGuest()) soakTools(dt);
    updateChatter();
    if (!isGuest()) {
      updateHelper(dt);
      maybePumpHelp();
      updatePumpHelp(dt);
    }
    for (let k = drives.length - 1; k >= 0; k--) {
      const d = drives[k];
      d.t += dt / d.seconds;
      const e = 1 - Math.pow(1 - Math.min(1, d.t), 3);
      d.group.position.x = lerp(d.from, d.to, e);
      if (d.t >= 1) { drives.splice(k, 1); if (d.done) d.done(); }
    }
    if (!isGuest()) updateWalkers(dt);
    updateGestures(dt);
    updateLoo(dt);
    updateNeedsAct(dt);
    updateVanCab();
    updatePhoneDrop(dt);
    // the drum turns about its own axis: slowly one way to keep the load mixed, faster the other
    // way to bring it up and out while it pours
    if (mixer.visible) drumSpin.rotation.x += dt * (streamOn ? -2.4 : 0.7);
    if (tripod.visible && gs.laserSetup !== 1) laserHead.rotation.y += dt * 6;
    laserHead.rotation.z = gs.laserSetup === 1 ? 0.12 + Math.sin(toolT * 1.3) * 0.02 : 0;
    beam.visible = gs.laserOn && laserWorks();
    // blowout drains the edge
    if (gs.blowout && gs.phase === 'pour' && !isGuest()) {
      gs.cells.forEach((c) => { if (gs.blowout.cells.has(c.idx) && c.fill > 0) { c.fill = Math.max(0, c.fill - 14 * dt); } });
      cellsDirty = true;
    }
    if (gs.phase === 'pour' && !isGuest()) flowTick(dt);
    // soup levels itself, slowly
    if (gs.phase === 'pour' && (gs.mixState === 'soup' || gs.water) && !isGuest()) {
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
    // (the splash where the hose lands is drawStream's)
    // and at the other end, the mixer's chute running into the pump's hopper while it pumps
    if (streamOn && mixer.visible && pump.visible && !drives.some((d) => d.group === mixer)) {
      for (let k = 0; k < 3; k++) emit(mixer.position.x - 4.55 + rnd(-0.08, 0.08), 1.72, mixer.position.z + rnd(-0.12, 0.12), rnd(-0.5, -0.2), rnd(-0.4, 0), rnd(-0.1, 0.1), 0.35, pick([0x7d7f80, 0x8c8e90]), rnd(0.05, 0.08));
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
      if (mixer.visible) emit(mixer.position.x + 1.3, 3.1, mixer.position.z - 1.0, rnd(-0.2, 0.2), rnd(0.8, 1.3), rnd(-0.2, 0.2), 1.5, 0x6d7074, 0.14, 0.25);
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
    updateClouds(dayness);
  }

  let shake = 0;
  function placeCamera(dt) {
    let y = EYE;
    const c = cellAt(player.x, player.z);
    // standing on it once it carries you; in it, up to the ankles, while it is wet
    if (c) y += groundY(player.x, player.z) * (gs.phase === 'pour' ? 0.3 : 0.9);
    else y += rampY(player.x, player.z);
    if (gs.tool === 'rideOn') y = 1.62 + groundY(player.x, player.z);
    const knelt = kneeling();
    if (knelt && !wasKneeling && gs.poured && onSlab(player.x, player.z)) stamp('knee', player.x, player.z, player.yaw, true);
    wasKneeling = knelt;
    kneel = lerp(kneel, knelt ? 1 : 0, 1 - Math.exp(-dt * 6));
    y -= kneel * 0.85;
    let roll = 0, lookDown = 0;
    if (player.fall > 0) {
      player.fall -= dt;
      if (player.stumble) {
        // a stumble: a lurch forward and a catch, not the floor
        const f = Math.sin(Math.PI * clamp(1 - player.fall / player.stumble, 0, 1));
        y -= 0.35 * f;
        roll = 0.16 * f;
        lookDown = 0.4 * f;
        if (player.fall <= 0) player.stumble = 0;
      } else {
        const f = player.fall > 1.2 ? (1.8 - player.fall) / 0.6 : player.fall > 0.4 ? 1 : player.fall / 0.4;
        y -= 1.2 * f;
        roll = 0.5 * f;
      }
    }
    // the morning after: the world won't keep still, and now and then it all comes back up
    const hang = hangoverNow();
    if (hang > 0 && !CALM) {
      const t = performance.now() / 1000;
      roll += Math.sin(t * 0.83) * 0.05 * hang + Math.sin(t * 2.1) * 0.012 * hang;
      lookDown += Math.sin(t * 0.61) * 0.03 * hang;
    }
    if (retch.t > 0) {
      const f = Math.sin(Math.PI * clamp(1 - retch.t / retch.len, 0, 1));
      lookDown += 0.75 * f;
      y -= 0.25 * f;
    }
    // needing it: from foot to foot when you stand, a sway when you walk; a cramp folds you over
    if (!CALM && gs.needs && gs.phase !== 'title' && gs.phase !== 'end') {
      const tn = performance.now() / 1000, w = urgePiss();
      if (w > 0) { if (!player.moving) y += Math.abs(Math.sin(tn * 5.2)) * 0.035 * w; roll += Math.sin(tn * 2.6) * 0.018 * w; }
      if (cramp.t > 0) { const f = Math.sin(Math.PI * clamp(1 - cramp.t / cramp.len, 0, 1)); lookDown += 0.42 * f; y -= 0.2 * f; }
    }
    if (gs.waitMode === 'van') y = 1.72;
    const bob = CALM ? 0 : Math.sin(player.bob) * 0.035;
    // something just went bang: the view shakes, then settles
    const sh = CALM ? 0 : shake;
    shake = Math.max(0, shake - dt * 1.2);
    camera.position.set(player.x + rnd(-1, 1) * sh * 0.05, y + (player.moving ? bob : 0) + rnd(-1, 1) * sh * 0.04, player.z + rnd(-1, 1) * sh * 0.05);
    camera.rotation.set(player.pitch - lookDown + rnd(-1, 1) * sh * 0.02, player.yaw, roll + rnd(-1, 1) * sh * 0.03);
    camera.updateMatrixWorld();
    placeTools(dt);
    updateFlights(dt);
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
  // a tool that has just come into your hands comes up into view from below, not out of thin air
  let equipT = 0, equipped = 'hands', shovelPh = 0, lastCut = 0, staffBeep = 0;
  const smooth = (a, b, t) => { const k = clamp(t, 0, 1); return a + (b - a) * k * k * (3 - 2 * k); };
  // the shovel's stroke, as poses: stab in, lever up with a load, lift across, tip it out, back
  const SHOVEL_POSES = [
    [0, [0, 0, 0, 0, 0, 0]],
    [0.22, [0.02, -0.12, -0.24, -0.4, 0, 0]],
    [0.42, [0, -0.06, -0.14, 0.38, 0, 0]],
    [0.62, [-0.14, 0.14, -0.06, 0.25, 0.5, 0.2]],
    [0.78, [-0.22, 0.16, -0.08, 0.05, 0.8, 1.0]],
    [1, [0, 0, 0, 0, 0, 0]],
  ];
  function shovelPose(ph) {
    let k = 0;
    while (k < SHOVEL_POSES.length - 2 && ph > SHOVEL_POSES[k + 1][0]) k++;
    const [t0, a] = SHOVEL_POSES[k], [t1, b] = SHOVEL_POSES[k + 1];
    const f = (ph - t0) / (t1 - t0);
    return a.map((v, n) => smooth(v, b[n], f));
  }
  const handPos = new THREE.Vector3();
  /** What the hands are doing at a job marker, when they are doing one. */
  function markerAnim() {
    if (input.action && lastCtxKind === 'edge' && edgeSpot) return 'edger';
    if (!nearMarker || !input.action || lastCtxKind !== 'marker') return null;
    const id = nearMarker.id;
    if (/^form|^block|^blowout/.test(id)) return 'hammer';
    if (id.startsWith('edge')) return gs.tool === 'handTrowel' ? 'edger' : null;
    if (id.startsWith('tie')) return 'tie';
    if (id.startsWith('cut')) return 'cut';
    if (id === 'wash') return 'wash';
    if (id === 'laserBench' || id.startsWith('laserCheck')) return 'staff';
    return null;
  }
  const tmpAxis = new THREE.Vector3(), tmpUp = new THREE.Vector3(0, 1, 0), tmpQ = new THREE.Quaternion();
  /** Down on one knee: hand troweling, and tying the mesh. */
  function kneeling() { const a = markerAnim(); return a === 'edger' || a === 'tie'; }
  function moveMachine(t, R, tx, tz, running) {
    // a spot that already doesn't fit (lifted over a board, say) lets it go anywhere
    const stuck = !discFits(t.x, t.z, R, running);
    // on the slab, the boards hold it in: pushed at one it runs along it, which is how an edge
    // gets done. It goes over a board only with you, stopped, walking off the slab with it.
    const held = onSlab(t.x, t.z) && (running || onSlab(player.x, player.z));
    const ok = (x, z) => (!held || (onSlab(x, z) && (stuck || edgeClearance(x, z) >= R + 0.01))) && (stuck || discFits(x, z, R, running));
    if (ok(tx, tz)) { t.x = tx; t.z = tz; return; }
    // as far towards it as it will go — up against the board — then along whatever stopped it
    let lo = 0, hi = 1;
    for (let k = 0; k < 6; k++) { const m = (lo + hi) / 2; if (ok(lerp(t.x, tx, m), lerp(t.z, tz, m))) lo = m; else hi = m; }
    t.x = lerp(t.x, tx, lo); t.z = lerp(t.z, tz, lo);
    if (ok(tx, t.z)) t.x = tx; else if (ok(t.x, tz)) t.z = tz;
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
    if (t !== equipped) { equipped = t; equipT = CALM ? 0 : 0.35; }
    equipT = Math.max(0, equipT - dt);
    const lift = equipT > 0 ? -0.3 * (equipT / 0.35) * (equipT / 0.35) : 0;
    // running pumps the arms, a hangover makes them wander
    const runBob = player.run > 0.3 && player.moving && !CALM ? Math.sin(player.bob * 1.6) * 0.03 * player.run : 0;
    hands.position.set(hx + sway, hy + Math.abs(sway) * 0.6 + lift + runBob, -0.62);
    hands.visible = !inVanNow();
    viewTools.pipe.visible = gs.carrying === 'pipe';
    viewTools.pipe.rotation.z = sway * 2;
    viewTools.laser.visible = gs.carrying === 'laser';
    viewTools.laser.rotation.z = sway * 2;
    viewTools.hose.visible = t === 'hose' && !gs.carrying && anim !== 'wash';
    viewTools.washHose.visible = anim === 'wash';
    viewTools.hammer.visible = t === 'hammer';
    viewTools.shovel.visible = t === 'shovel';
    // the hands move in on a tall screen; the shovel stays where it's easy to see
    const digging = t === 'shovel' && input.action && (lastCtxKind === 'shovel' || lastCtxKind === 'unspill');
    if (digging || shovelPh > 0) {
      const was = shovelPh;
      shovelPh = (shovelPh + dt / 1.25) % 1;
      if (!digging && shovelPh < was) shovelPh = 0;       // finish the stroke, then stop
      const [px, py, pz, rx, ry, rz] = shovelPose(shovelPh);
      const pv = viewTools.shovel.userData.pivot;
      viewTools.shovel.position.set(0.32 - hands.position.x, 0, 0);
      pv.position.copy(pv.userData.home).add(tmpV.set(px * 1.3, py * 1.3, pz));
      pv.rotation.set(rx, ry, rz);
      const load = viewTools.shovel.userData.load;
      if (load) load.visible = shovelPh > 0.36 && shovelPh < 0.76;
      if (was < 0.2 && shovelPh >= 0.2) sfx('stab');
      if (was < 0.72 && shovelPh >= 0.72) sfx('slop');
      if (was < 0.74 && shovelPh >= 0.74 && (target || lastCtxKind === 'unspill')) {
        // the load leaves the blade, off to the side
        const o = camera.localToWorld(new THREE.Vector3(-0.35, -0.2, -0.9));
        for (let k = 0; k < 10; k++) emit(o.x, o.y, o.z, -Math.cos(player.yaw) * rnd(1, 2) + rnd(-0.3, 0.3), rnd(0.5, 1.5), Math.sin(player.yaw) * rnd(1, 2) + rnd(-0.3, 0.3), 0.8, 0x7d7f80, rnd(0.04, 0.08));
      }
    } else {
      viewTools.shovel.position.set(0.32 - hands.position.x, 0, 0);
      const pv = viewTools.shovel.userData.pivot;
      pv.position.copy(pv.userData.home);
      pv.rotation.set(0, 0, 0);
      if (viewTools.shovel.userData.load) viewTools.shovel.userData.load.visible = false;
    }
    viewTools.pliers.visible = t === 'pliers';
    viewTools.cutter.visible = t === 'cutter';
    // the staff: up, the receiver hunting for the beam, beeping faster as it closes in, then steady
    viewTools.staff.visible = anim === 'staff';
    if (anim === 'staff') {
      const frac = clamp(holdT / Math.max(0.1, holdOf(nearMarker)), 0, 1);
      viewTools.staff.position.y = 0.05 + Math.sin(frac * Math.PI * 3) * 0.05 * (1 - frac);
      staffBeep -= dt;
      if (staffBeep <= 0) { staffBeep = lerp(0.45, 0.07, frac); sfx('rx'); }
      rxLight.material = lam(frac > 0.85 ? 0x3ec16a : Math.sin(toolT * 20) > 0 ? 0x3e8ed8 : 0xd84a2a);
    }
    // the hose kicks with every stroke of the pump
    if (viewTools.hose.visible) {
      const kick = streamOn && !CALM ? Math.sin(toolT * 50) * 0.008 + Math.max(0, Math.sin(toolT * 7.5)) * 0.025 : 0;
      viewTools.hose.position.set(kick * 0.2, kick * 0.5, 0);
    }
    if (viewTools.washHose.visible) {
      const jet = CALM ? 0 : Math.sin(toolT * 40) * 0.004;
      viewTools.washHose.position.set(jet, jet, 0);
    }
    if (anim === 'hammer') {
      // wind up slow, come down fast, bounce: about two and a half a second
      const ph = (toolT * 2.4) % 1;
      const a = ph < 0.6 ? lerp(-1.1, 0.7, ph / 0.6) : ph < 0.72 ? lerp(0.7, -1.2, (ph - 0.6) / 0.12) : lerp(-1.2, -1.1, (ph - 0.72) / 0.28);
      viewTools.hammer.rotation.x = a;
      viewTools.hammer.rotation.z = ph < 0.6 ? -0.15 * (ph / 0.6) : -0.15;
      if (ph >= 0.72 && lastSwing < 0.72) {
        sfx(nearMarker.id === 'block' ? 'clank' : 'hammer');
        // every blow jolts the view, and knocks sawdust (or rust) off
        shake = Math.max(shake, 0.12);
        const o = camera.localToWorld(new THREE.Vector3(0.1, -0.35, -0.75));
        for (let k = 0; k < 5; k++) emit(o.x, o.y, o.z, rnd(-0.6, 0.6), rnd(0.4, 1.2), rnd(-0.6, 0.6), 0.5, nearMarker.id === 'block' ? 0x8a5a3a : 0xd9b27a, rnd(0.01, 0.02));
      }
      lastSwing = ph;
    } else { lastSwing = 0; viewTools.hammer.rotation.x = -0.6; }
    // at a corner or an edge the trowel is on the concrete itself, sweeping arcs into the corner
    // and along the edge; the view eases round to it, as you would look at what you are doing
    workTrowel.visible = workArm.visible = anim === 'edger';
    if (anim === 'edger') {
      // a corner or collar job, or the metre of board the trowel is on
      const sp = lastCtxKind === 'edge' && edgeSpot;
      const e = sp ? { inX: sp.inX, inZ: sp.inZ, alongX: sp.ax, alongZ: sp.az } : site.edges[+nearMarker.id.slice(4)] || {};
      const at = sp ? P(sp.bx + sp.ax * sp.along + sp.inX * 0.1, sp.bz + sp.az * sp.along + sp.inZ * 0.1) : nearMarker;
      const inX = e.inX || 0, inZ = e.inZ || 0;
      const sw = Math.sin(toolT * 5), al = e.alongX !== undefined ? P(e.alongX, e.alongZ) : P(-inZ, inX);
      const wx = at.x + al.x * sw * 0.18 + inX * (0.06 + Math.cos(toolT * 5) * 0.04);
      const wz = at.z + al.z * sw * 0.18 + inZ * (0.06 + Math.cos(toolT * 5) * 0.04);
      workTrowel.position.set(wx, groundY(wx, wz) + 0.006, wz);
      workTrowel.rotation.set(0, Math.atan2(-al.z, al.x) + sw * 0.35, 0.04);
      handPos.set(0.22, -0.42, -0.25);
      camera.localToWorld(handPos);
      tmpV2.set(wx, workTrowel.position.y + 0.06, wz);
      stretch(workArm, handPos, tmpV2);
      // the view eases down to the work; at a corner it turns round to it too, along a board
      // which way you face is yours
      const d = Math.max(0.3, hyp(at.x, at.z, player.x, player.z));
      const k = 1 - Math.exp(-dt * 3);
      if (!sp) player.yaw = turnTo(player.yaw, Math.atan2(-(at.x - player.x), -(at.z - player.z)), k);
      player.pitch = lerp(player.pitch, -Math.atan2(camera.position.y - groundY(at.x, at.z), d), k);
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
    // the cutter: squeeze slowly, the bar gives with a snap, the handles spring back
    let squeeze = 0;
    if (anim === 'cut') {
      const ph = (toolT * 0.9) % 1;
      squeeze = ph < 0.7 ? smooth(0, 0.85, ph / 0.7) : ph < 0.74 ? 1 : smooth(1, 0, (ph - 0.74) / 0.26);
      if (ph >= 0.7 && lastCut < 0.7) {
        sfx('snip');
        shake = Math.max(shake, 0.08);
        const o = camera.localToWorld(new THREE.Vector3(-0.3, -0.05, -1.05));
        for (let k = 0; k < 4; k++) emit(o.x, o.y, o.z, rnd(-0.8, 0.8), rnd(0.5, 1.6), rnd(-0.8, 0.8), 0.7, 0xffd08a, 0.012);
      }
      lastCut = ph;
    } else lastCut = 0;
    viewTools.cutter.position.y = anim === 'cut' ? -0.04 * squeeze : 0;
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

    showDirt();
    // hand tools lying about
    ['float', 'handTrowel', 'hammer', 'pliers', 'cutter', 'hose', 'shovel'].forEach((id) => {
      const tl = gs.tools[id], g = lying[id];
      g.visible = !!tl && tl.in === 'ground';
      if (g.visible) {
        const ry = rampY(tl.x, tl.z);
        g.position.set(tl.x, groundY(tl.x, tl.z) + (ry ? 0.02 : 0), tl.z);
        if (ry) g.quaternion.setFromAxisAngle(tmpAxis.set(Math.sin(van.rotation.y), 0, Math.cos(van.rotation.y)), RAMP_DOWN).multiply(tmpQ.setFromAxisAngle(tmpUp, tl.yaw));
        else g.rotation.set(0, tl.yaw, 0);
      }
    });

    // the machines: parked where they were left, or running where you steer them
    const running = input.action && lastCtxKind === 'trowel';
    ['trowelSmall', 'trowelBig', 'rideOn'].forEach((id) => {
      const m = machines[id], tl = gs.tools[id];
      m.group.visible = !!tl && tl.in !== 'van';
      if (!m.group.visible) return;
      const inHand = tl.in === 'hand' && (tl.by ? tl.by === net.me : gs.tool === id);
      const remoteOn = tl.in === 'hand' && !inHand && crewActing(tl.by, 'trowel');
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
      const on = (inHand && running) || remoteOn;
      m.spin = lerp(m.spin, gs.broken[id] ? 0 : on ? 17 : inHand && !gs.fitting ? 5 : 0, 1 - Math.exp(-dt * 3));
      if (gs.broken[id] && chance(dt * 3)) emit(tl.x, 0.6, tl.z, rnd(-0.2, 0.2), rnd(0.6, 1.2), rnd(-0.2, 0.2), 1.8, 0x3a3c3f, 0.12, -0.1);
      const fit = gs.fit[id];
      m.rotors.forEach((r, k) => {
        r.rotor.rotation.y += (k ? 1 : -1) * m.spin * dt;
        r.pan.visible = fit === 'pans';
        r.blades.visible = fit === 'blades';
        // the blades tilt up as the concrete gets harder, the way a finisher sets them
        r.blades.children.forEach((arm) => { arm.children[0].rotation.x = 0.04 + clamp((hAt(tl.x, tl.z) - 55) / 40, 0, 1) * 0.16; });
      });
      if (m.beacon) m.beacon.material.emissive.setHex(on || (inHand && !gs.fitting) ? (Math.sin(toolT * 9) > 0.2 ? 0xff6a00 : 0x401800) : 0x000000);
      const buzz = inHand && !CALM && !gs.fitting ? (on ? Math.sin(toolT * 71) * 0.004 : Math.sin(toolT * 43) * 0.0015) : 0;
      m.group.position.set(tl.x, groundY(tl.x, tl.z) + buzz, tl.z);
      // tipped back on its handle while the pans or blades are changed
      const tip = gs.fitting && gs.fitting.id === id ? -0.35 : 0;
      m.group.rotation.set(m.twin ? 0 : tip, tl.yaw, on && !CALM ? Math.sin(toolT * 11) * 0.012 : 0, 'YXZ');
      if (on && hAt(tl.x, tl.z) < 70 && chance(dt * 30 * m.rotors.length)) {
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
      if (!p.unload) left.push('open the van');
      if (p.form.includes(false)) left.push('check the formwork (hammer)');
      if (!p.laser) {
        const st = gs.laserSetup || 0;
        left.push(st === 0 ? (gs.laserInVan ? 'take the laser out of the van and set up the tripod' : 'set up the tripod') : st === 1 ? 'level the laser (bubble in the middle)'
          : st === 2 ? 'shoot the benchmark (the painted peg)' : `check the board heights (${site.levelChecks.filter((c) => !c.done).length} corners left)`);
      }
      if (site.levelChecks && site.levelChecks.some((c) => c.done && Math.abs(c.off) > 4 && !c.fixed)) left.push('knock the board that\'s out back to height (hammer)');
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
        if (gs.pipes < PIPE_N && day.boom) return `The pump driver is setting up the boom.<small>${nextPrep().length ? `Still open: ${nextPrep().join(', ')}` : 'Nothing to carry today. Coffee?'}</small>`;
        if (gs.pipes < PIPE_N) return (gs.carrying ? `Carry the pipe to marker ${gs.pipes + 1}.` : `Grab a pipe from the pile (${gs.pipes}/${PIPE_N} laid).`) + (nextPrep().length ? `<small>Still open: ${nextPrep().join(', ')}</small>` : '');
        return `Line laid. Waiting for the mixer.<small>Truck due ${clock(gs.nextTruckAt)}</small>`;
      case 'pour': {
        if (gs.blocked >= 0) return 'The line is blocked. Find the red marker and hit the pipe!';
        if (gs.blowout) return `The formwork burst on the ${gs.blowout.name}. Fix it before you lose more!`;
        if (!laserWorks() && gs.prep.laser) return 'Laser batteries are dead. Spares are in the van.';
        const need = volumeNeeded(), inn = Math.min(need, pouredIn());
        const first = firstLoad(), ready = first && first.h >= 15 ? `Truck ${first.no}'s end is at ${Math.floor(first.h)}%: ${first.h >= 25 ? 'pans, edges and corners' : 'edges and corners'} can start.` : '';
        const sub = gs.tool !== 'hose' && gs.tools.hose && gs.tools.hose.in === 'ground' && gs.truck && !gs.truck.waiting ? `Pick up the hose at the end of the line.${ready && isMachine(gs.tool) ? ' Or trowel between trucks.' : ''}` : gs.truck && !gs.truck.waiting ? ready || `Hose: pour · Float (van): level · Laser shows the height`
          : gs.truck ? `Truck ${gs.truck.no} is here. ${ready || 'Float what you have while it backs up.'}`
          // the last truck gone and nothing ordered: no time that has already been and gone
          : gs.nextTruckAt >= gs.t ? `Next truck due ${clock(gs.nextTruckAt)}. ${ready ? ready : 'Float what you have.'}` : 'No more concrete coming. Float what\'s there, then finish the pour.';
        return `Pour to ${day.thick} mm: ${inn.toFixed(1)} of ${need.toFixed(1)} m³ in, ±${rms().toFixed(1)} mm.<small>${sub}</small>`;
      }
      case 'wash': if (!gs.gaveUp) { const d = dirtyTools().filter((t) => !isMachine(t)); return `Wash ${d.length ? theList(d) : 'your tools'} at the water tank before the concrete sets on ${d.length > 1 ? 'them' : 'it'}.<small>Carry each one there and hold Wash. The float and the hand trowel still work on the slab meanwhile.</small>`; }
        if (gs.gaveUp) return gs.packing ? 'Throwing the tools in the van.' : 'It\'s gone off, tools unwashed. Walk to the van and go home.<small>They\'ll be concrete tools now. Very sturdy.</small>'; return 'Wash your tools at the water tank before they set.<small>The pump driver does his own pipes. The laser can be packed up any time now.</small>';
      case 'cure': {
        if (gs.packing) return 'Throwing the tools in the van.<small>Therapy, but cheaper.</small>';
        if (gs.gaveUp) return 'It\'s gone off. Nothing more goes on this slab today.<small>Walk to the van: the tools go in, you go home.</small>';
        const marks = gs.cells.filter((c) => c.marks.length && !c.defect).length;
        const warn = marks && gs.H < 80 ? `<small>${marks} m² with marks — ${gs.H < 50 ? 'float or pan' : 'pan'} them out before 80%.</small>` : '';
        if (gs.H < 25) return `Pans from 25% (${Math.floor(gs.H)}% now).${warn || `<small>Meanwhile: float out marks, trowel the edges and corners from 15%, guard it, eat or nap.</small>`}`;
        const mt = machineTool();
        if (!gs.panPasses.length) {
          if (!mt) return `Pan pass: take a power trowel from behind the van.${warn || '<small>They come with pans on.</small>'}`;
          if (gs.fit[mt] !== 'pans') return `Fit the pans (the button next to Put down).${warn}`;
          return `Run it over every orange square.${warn || '<small>Hold the big button; slide your thumb on it to steer.</small>'}`;
        }
        if (edgeWorkLeft()) return `Edges all round: ${edgeWorkText()} to go.${warn || '<small>Along the boards: the edge trowel or the hand trowel. Corners and collars: the hand trowel.</small>'}`;
        if (!gs.bladePasses.length) {
          if (gs.H < 55) return `Wait for blades (55%).${warn || '<small>Another pan pass flattens it more.</small>'}`;
          if (!mt) return `Blade pass: take a power trowel and fit the blades.${warn}`;
          if (gs.fit[mt] !== 'blades') return `Fit the blades (the button next to Put down).${warn}`;
          return `Blades: every orange square again.${warn}`;
        }
        if (gs.prep.laser && !gs.laserPacked) return `Pack up the laser and put it in the van.<small>${gs.H < 95 ? `${dur(etaTo(95))} to 95%.` : 'Then home.'}</small>`;
        if (gs.H < 95) return `Wait for 95%, then go home.<small>${dur(etaTo(95))} to go. Another blade pass shines it up.</small>`;
        const out = toolsOut(), dirty = dirtyTools();
        if (out.length || dirty.length) return `Pack up: ${out.length ? `bring ${theList(out)} back to the van` : ''}${out.length && dirty.length ? '; ' : ''}${dirty.length ? `wash ${theList(dirty)}` : ''}.<small>Then home. The manager checks the van. The manager always checks the van.</small>`;
        return isGuest() ? `It's 95% and the van is packed. ${net.hostName} calls it a day.` : 'It\'s 95%, the van is packed. Go home.';
      }
      default: return '';
    }
  }
  const TOOL_ICON = { hose: 'hose', float: 'float', handTrowel: 'trowel', hammer: 'hammer', pliers: 'pliers', cutter: 'cutter', shovel: 'shovel', trowelSmall: 'machine', trowelBig: 'machine', rideOn: 'machine' };
  const hud = {
    clockbox: $('#clockbox'), hHour: $('#hHour'), hMin: $('#hMin'), phaseTxt: $('#phaseTxt'), objective: $('#objective'), objText: $('#objText'), objFill: $('#objFill'),
    wTemp: $('#wTemp'), wRh: $('#wRh'), wWind: $('#wWind'), wWindIco: $('#wWindIco'), wSlab: $('#wSlab'), wIndoor: $('#wIndoor'), wNeed: $('#wNeed'),
    energyFill: $('#energyFill'), cupsTxt: $('#cupsTxt'), truckLine: $('#truckLine'), truckSub: $('#truckSub'), truckBig: $('#truckBig'), truckFill: $('#truckFill'),
    toolIco: $('#toolIco'), toolTxt: $('#toolTxt'), altIco: $('#altIco'), altTxt: $('#altTxt'), waitTxt: $('#waitTxt'), cupsBadge: $('#cupsBadge'),
    crosshair: $('#crosshair'), mapN: $('#mapN'),
  };
  let objHtml = '', objKey = '', phaseKey = '', truckMax = {};
  /** How far through the job in hand: the prep list, the pipes, the pour, then the set. */
  function phaseProgress() {
    switch (gs.phase) {
      case 'prep': {
        const p = gs.prep, total = 2 + p.form.length + site.ties.length + site.cuts.length;
        const done = (p.unload ? 1 : 0) + p.form.filter(Boolean).length + (p.laser ? 1 : (gs.laserSetup || 0) / 4)
          + site.ties.filter((t) => t.done).length + site.cuts.filter((t) => t.done).length;
        return done / total;
      }
      case 'pipes': return gs.pipes / PIPE_N;
      case 'pour': return pouredIn() / volumeNeeded();
      case 'wash': case 'cure': return gs.H / 95;
      default: return 0;
    }
  }
  function updateHUD(dt) {
    hudT -= dt;
    if (hudT > 0) return;
    hudT = 0.12;
    $('#clock').textContent = clock(gs.t);
    hud.hHour.setAttribute('transform', `rotate(${((gs.t % 720) / 720) * 360} 20 20)`);
    hud.hMin.setAttribute('transform', `rotate(${((gs.t % 60) / 60) * 360} 20 20)`);
    const pk = gs.phase === 'cure' && gs.H >= 25 ? 'trowel' : gs.phase;
    if (pk !== phaseKey) {
      phaseKey = pk;
      hud.clockbox.dataset.phase = pk;
      hud.phaseTxt.textContent = { morning: 'Morning', prep: 'Prep', pipes: 'Pipes', pour: 'Pour', wash: 'Wash up', cure: 'Curing', trowel: 'Trowel', end: 'Home' }[pk] || '';
      restartAnim(hud.clockbox, 'flip');
    }
    // the task card: it flashes when the job changes, not every time a number in it ticks over
    const oh = objective() || (gs.phase === 'morning' ? 'Up, dressed, out. Be on site before anybody else.<small>The alarm has opinions about that.</small>' : '');
    if (oh !== objHtml) {
      objHtml = oh;
      hud.objText.innerHTML = oh;
      const key = oh.replace(/<small>[\s\S]*$/, '').replace(/[\d.,±–%:-]+/g, '#');
      if (key !== objKey) { if (objKey) restartAnim(hud.objective, 'fresh'); objKey = key; }
    }
    hud.objFill.style.width = `${Math.round(clamp(phaseProgress(), 0, 1) * 100)}%`;
    // everything under the task box moves down when its text runs to another line
    const topH = $('#top').offsetHeight;
    if (topH !== hudTopH) { hudTopH = topH; $('#hud').style.setProperty('--hud-top', `${10 + topH + 8}px`); }
    hud.wTemp.textContent = `${tempAt(gs.t).toFixed(1)}°`;
    hud.wRh.textContent = `${day.rh}%`;
    hud.wWind.textContent = `${day.wind}`;
    hud.wWindIco.style.setProperty('--ws', `${(3.2 / Math.max(1, day.wind)).toFixed(2)}s`);
    hud.wSlab.textContent = `${day.thick} mm`;
    hud.wIndoor.hidden = !day.indoor;
    const ac = gs.accident;
    const need = ac ? (ac.kind === 'shit' ? ['poo', ac.washed ? 'Cold and wet: spares in the van' : 'Shat yourself: tank, then van'] : ['wee', 'Pissed yourself: spares in the van'])
      : gs.needs.poo > 60 ? ['poo', 'Needs a shit, badly'] : gs.needs.wee > 60 ? ['wee', 'Needs a piss'] : null;
    hud.wNeed.hidden = !need;
    if (need && hud.wNeed.textContent !== need[1]) { hud.wNeed.className = need[0]; hud.wNeed.textContent = need[1]; }
    hud.energyFill.style.width = `${gs.energy}%`;
    hud.energyFill.className = gs.energy < 25 ? 'low' : gs.energy < 50 ? 'mid' : '';
    hud.cupsTxt.textContent = `${gs.cups} cup${gs.cups === 1 ? '' : 's'}`;
    // The hardness, from the first truck on: mid-pour the end that went in first is going off while
    // the last is still coming, and that's the end to get the trowels on.
    const hard = $('#hard'), first = firstLoad();
    const showHard = gs.poured || (gs.phase === 'pour' && !!first);
    hard.hidden = !showHard;
    hard.classList.toggle('mid', !gs.poured);
    if (showHard) {
      const [lo, hi] = spreadH(), mean = gs.poured ? gs.H : meanH();
      $('#hardPct').textContent = hi - lo >= 3 ? `${Math.floor(lo)}–${Math.floor(hi)}%` : `${Math.floor(mean)}%`;
      $('#hardFill').style.width = `${mean}%`;
      $('#hardFirst').style.left = `${hi}%`;
      $('#hardFirst').hidden = hi - lo < 3;
      // mid-pour it has to share the side with the truck: short, and about the first end
      let eta;
      if (!gs.poured) {
        const h = first.h;
        eta = h < 15 ? `Edges in <b>${dur(etaTo(15, h, first.rate, first.fast))}</b>` : h < 25 ? `<b>Edges now</b> · pans in <b>${dur(etaTo(25, h, first.rate, first.fast))}</b>` : h < 55 ? '<b>Pans and edges now</b>' : '<b>Blades now</b>';
      } else eta = gs.H < 25 ? `Pans in <b>${dur(etaTo(25))}</b>` : gs.H < 55 ? `Blades in <b>${dur(etaTo(55))}</b>` : gs.H < 95 ? `95% in <b>${dur(etaTo(95))}</b>` : '<b>Hard enough to leave</b>';
      // each truck's concrete on its own, the hardest first, once they've drifted apart
      const loads = gs.loads.filter((l) => l && l.at !== null).sort((a, b) => b.h - a.h);
      // an arrow on a load that's in, level and going off on a big pour
      const trucks = loads.length > 1 && hi - lo >= 3 ? `<br>${loads.slice(0, 4).map((l) => `T${l.no} <b>${Math.floor(l.h)}%${l.fast && !gs.poured ? '↑' : ''}</b>`).join(' · ')}` : '';
      const [ed, ea] = edgeMetres(), edging = gs.tool === 'handTrowel' || gs.tool === 'trowelSmall';
      const edges = `<i class="nw">Edges <b>${ed}/${ea} m</b></i>${site.edges.length && gs.poured ? ` · <i class="nw">corners <b>${gs.edgesDone}/${site.edges.length}</b></i>` : ''}`;
      const passes = gs.poured ? `<br>Pans ${gs.panPasses.length} · blades ${gs.bladePasses.length}<br>${edges}<br>Flatness <b>±${rms().toFixed(1)} mm</b>` : edging ? `<br>${edges}` : '';
      const cov = machineTool() ? `<br>${fitted() === 'pans' ? 'Pan' : 'Blade'} pass <b>${Math.round(passCoverage(passKey()) * 100)}%</b>` : '';
      $('#hardEta').innerHTML = eta + trucks + passes + cov;
    }
    const ti = $('#truckInfo');
    ti.hidden = !(gs.phase === 'pour' || (gs.phase === 'pipes' && gs.pipes === PIPE_N));
    // both on the side at once: sideways, the truck's panel keeps to the truck
    $('#hud').classList.toggle('both', !ti.hidden && showHard);
    if (!ti.hidden) {
      const low = gs.cells.reduce((sum, c) => sum + Math.max(0, day.thick - c.fill), 0) / 1000;
      if (truckMax.gs !== gs) truckMax = { gs };
      if (gs.truck) {
        const no = gs.truck.no, left = Math.max(0, gs.truck.left);
        truckMax[no] = Math.max(truckMax[no] || 0, left);
        hud.truckLine.textContent = `Truck ${no} · left`;
        hud.truckBig.textContent = `${left.toFixed(1)} m³`;
        hud.truckFill.style.width = `${truckMax[no] ? (left / truckMax[no]) * 100 : 0}%`;
      } else {
        const coming = gs.nextTruckAt >= gs.t;
        hud.truckLine.textContent = coming ? 'Next truck' : 'More trucks';
        hud.truckBig.textContent = coming ? clock(gs.nextTruckAt) : 'none';
        hud.truckFill.style.width = '0%';
      }
      hud.truckSub.textContent = `low spots need ≈ ${low.toFixed(1)} m³`;
      ti.classList.toggle('pouring', !!streamOn);
      ti.classList.toggle('away', !gs.truck);
    }
    $('#btnFinish').hidden = !(gs.phase === 'pour' && filledShare() >= 0.97);
    // what is in your hands, and the one thing you can do to it
    const h = held();
    const nt = !gs.carrying && nearTool ? TOOLS[nearTool] : null;
    const toolLabel = gs.carrying ? `Carrying<small>${gs.carrying === 'pipe' ? 'a pipe' : 'the laser'}</small>`
      : nt ? `${nt.ride ? 'Get on' : h ? 'Swap' : 'Pick up'}<small>${nt.name}${nt.machine ? ` · ${gs.fit[nearTool]}` : ''}</small>`
      : !h ? 'Hands<small>empty</small>' : `${TOOLS[h].ride ? 'Get off' : 'Put down'}<small>${TOOLS[h].name}</small>`;
    if (toolLabel !== btnState.tool) {
      hud.toolTxt.innerHTML = toolLabel;
      hud.toolIco.className = `ico i-${gs.carrying ? (gs.carrying === 'pipe' ? 'pipe' : 'laser') : nt ? TOOL_ICON[nearTool] : h ? TOOL_ICON[h] : 'hand'}`;
      btnTool.classList.toggle('dim', !nt && (!h || !!gs.carrying));
      btnTool.classList.toggle('ready', !!nt);
      btnState.tool = toolLabel;
    }
    const alt = isMachine(h) ? `${gs.fit[h] === 'pans' ? 'Fit blades' : 'Fit pans'}<small>${gs.fit[h]} on</small>` : laserUsable() ? 'Laser' : '';
    if (alt !== btnState.alt) { hud.altTxt.innerHTML = alt; hud.altIco.className = `ico i-${isMachine(h) ? 'machine' : 'laser'}`; btnAlt.hidden = !alt; btnState.alt = alt; }
    btnAlt.classList.toggle('on', alt === 'Laser' && gs.laserOn);
    // the waiting badge follows the state, whatever ended the wait
    const waitKey = `${gs.waitMode}|${gs.fastForward}`;
    if (waitKey !== btnState.wait) { btnState.wait = waitKey; showWait(); }
    hud.waitTxt.textContent = gs.waitMode || gs.fastForward ? 'Stop' : 'Wait';
    $('#btnWait').classList.toggle('on', !!(gs.waitMode || gs.fastForward));
    hud.cupsBadge.textContent = gs.cups;
    hud.cupsBadge.hidden = !gs.cups;
    $('#btnCoffee').classList.toggle('dim', !gs.cups);
    // the crosshair lights up over something to work on, and turns while you work it
    hud.crosshair.classList.toggle('on', btnState.idle === false);
    hud.crosshair.classList.toggle('act', btnState.idle === false && !!input.action);
    hud.mapN.style.setProperty('--yaw', `${player.yaw}rad`);
    speakerDir();
    // what you're looking at
    const info = $('#targetInfo');
    if (target && gs.pourStarted) {
      const d = target.fill - day.thick;
      const name = `${String.fromCharCode(65 + target.i)}${target.j + 1}`;
      // concrete that's been in a while: how hard it is, and whose it is
      const l = mixOf(target), many = gs.loads.filter(Boolean).length > 1;
      const hard = gs.phase === 'pour' && l && target.fill > 20 ? ` · ${Math.floor(l.h)}%${many ? ` · truck ${l.no}` : ''}` : '';
      if (gs.phase === 'pour' && gs.laserOn && laserWorks()) {
        const cls = Math.abs(d) <= 3 ? 'dev-ok' : d > 0 ? 'dev-hi' : 'dev-lo';
        info.innerHTML = `${name} · <span class="${cls}">${d > 0 ? '+' : ''}${d.toFixed(0)} mm</span>${hard}`;
      } else if (gs.phase === 'pour') {
        info.textContent = `${name} · ${target.fill < 5 ? 'empty' : hard ? hard.slice(3) : 'looks about right?'}`;
      } else {
        info.textContent = `${name}` + (gs.poured ? ` · ${Math.floor(cellH(target))}%${l && many ? ` · truck ${l.no}` : ''}` : '') + (target.marks.length ? ` · ${target.marks.length} mark${target.marks.length > 1 ? 's' : ''}` : '') + (target.defect ? ' · set in' : '');
      }
    } else info.textContent = nearMarker ? nearMarker.label : '';
    // at a job, its name under the crosshair in the job's own colour
    info.style.color = !(target && gs.pourStarted) && nearMarker ? nearMarker.col : '';
    drawMap();
  }
  /** Real time spent on the day, as on a timesheet. */
  function playedFor() {
    const sec = Math.round((gs.stats.playMs || 0) / 1000), h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), x = sec % 60;
    return h ? `${h} h ${String(m).padStart(2, '0')} min` : m ? `${m} min ${String(x).padStart(2, '0')} s` : `${x} s`;
  }
  /** Plays a class's animation again from the start. */
  function restartAnim(el, cls) { el.classList.remove(cls); void el.offsetWidth; el.classList.add(cls); }
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
    // the slab square by square: stones and mesh where nothing's poured yet, then dark and wet,
    // going pale as each truck's concrete goes off
    gs.cells.forEach((c) => {
      let col = '#8c8577';
      if (gs.poured || (gs.pourStarted && c.fill > 20)) { const v = Math.round(90 + cellH(c) * 1.2); col = `rgb(${v},${v},${v + 4})`; }
      poly(gx(c.i) - 0.02, gz(c.j) - 0.02, gx(c.i + 1) + 0.02, gz(c.j + 1) + 0.02, col);
    });
    // the edges that will take the trowel now and haven't had it
    if (gs.phase === 'pour' || gs.phase === 'wash' || gs.phase === 'cure') {
      g.strokeStyle = '#ff6b1a'; g.lineWidth = 3.5;
      g.beginPath();
      for (const c of gs.cells) {
        if (!(gs.poured || c.fill > 20) || cellH(c) < 15) continue;
        for (const [di, dj] of boardsOf(c)) {
          if (c.edged & sideBit(di, dj)) continue;
          const x0 = gx(c.i) + (di > 0 ? 1 : 0), z0 = gz(c.j) + (dj > 0 ? 1 : 0);
          const a = toMap(x0, z0), b = toMap(di ? x0 : x0 + 1, di ? z0 + 1 : z0);
          g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]);
        }
      }
      g.stroke();
    }
    const rpoly = (o, col) => {
      const pts = [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([a, b]) => { const [wx, wz] = turnXZ(a * o.hx, b * o.hz, o.ang); return toMap(o.cx + wx, o.cz + wz); });
      g.fillStyle = col; g.beginPath(); g.moveTo(pts[0][0], pts[0][1]);
      for (let k = 1; k < 4; k++) g.lineTo(pts[k][0], pts[k][1]);
      g.closePath(); g.fill();
    };
    layoutObs.forEach((o) => rpoly(o, o.col));
    if (hall.visible) hallWalls.forEach((o) => rpoly(o, '#b9b3a8'));
    if (pump.visible) poly(pump.position.x - 4.3, pump.position.z - 1.2, pump.position.x + 4.3, pump.position.z + 1.2, '#f2b705');
    if (mixer.visible) poly(mixer.position.x - 4.5, mixer.position.z - 1.2, mixer.position.x + 4.5, mixer.position.z + 1.2, '#ff6b1a');
    g.restore();
    const pulse = 5 + Math.sin(performance.now() / 200) * 1.5;
    markers.forEach((m) => {
      if (!m.active()) return;
      g.fillStyle = m.col || '#ff6b1a';
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
  // A frame that goes wrong must not take the game with it. The next frame is asked for first,
  // the day moves on in one guarded part and the picture is drawn in another: one bad frame is a
  // hiccup, not a black screen with the numbers frozen on it. What went wrong is kept, to be read
  // out of the pause menu.
  function frame(now) {
    requestAnimationFrame(frame);
    try { step(now); } catch (e) { hiccup(e); }
    try { renderer.render(scene, camera); } catch (e) { hiccup(e); }
  }
  let hiccups = 0, lastHiccup = '';
  function hiccup(e) {
    hiccups++;
    const where = String((e && e.stack) || '').split('\n').slice(1, 3).map((l) => l.trim().replace(/^at /, '').replace(/https?:\/\/[^\s)]*\//g, '')).join(' < ');
    const text = `${(e && e.message) || e}${where ? ` (${where})` : ''}`;
    if (text === lastHiccup) return;
    lastHiccup = text;
    let v = ''; try { v = appVersion(); } catch (e2) { /* before it's there */ }
    store('pourday.hiccup', `${new Date().toISOString().slice(0, 16)} ${v} ${text}`.slice(0, 400));
    if (DEBUG) console.error('hiccup', e);
  }
  window.addEventListener('error', (ev) => hiccup(ev.error || ev.message));
  window.addEventListener('unhandledrejection', (ev) => hiccup(ev.reason));
  function step(now) {
    // Up to a tenth of a second a frame: an older phone at 12 frames a second still plays in real
    // time, and a long stall (the app in the background) doesn't jump the day forward.
    const dt = Math.min(0.1, (now - last) / 1000);
    pumpSay();
    // real time played, for the report: not while paused, not with the app in the background
    if (gs.phase !== 'title' && gs.phase !== 'end' && !(modalOpen && modalOpen.pause) && !settingsOpen && document.visibilityState !== 'hidden') gs.stats.playMs = (gs.stats.playMs || 0) + Math.min(1000, now - last);
    fpsN++; fpsT += now - last;
    if (fpsT > 1000) { fpsNow = fpsN; fpsN = 0; fpsT = 0; }
    last = now;
    const playing = gs.phase !== 'title' && gs.phase !== 'end';
    if (playing && !modalOpen && !settingsOpen) {
      updateHangover(dt);
      updateMishaps();
      updateManager();
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
        // The name: high over the ring and full size from across the site; walking up to it, it
        // comes down to chest height and shrinks, so it stays in view and about the same size on
        // the screen instead of sailing off over your head.
        const near = clamp((hyp(m.x, m.z, player.x, player.z) - 1) / 6, 0, 1);
        // right at it, the name is under the crosshair already: no second copy in the air
        m.sprite.visible = m !== nearMarker;
        m.sprite.position.y = lerp(1.05, 2.7, near);
        const sw = m.w * lerp(0.26, 1, near);
        m.sprite.scale.set(sw, sw / 4, 1);
      });
    }
    if (cellsDirty) paintCells();
    // the overlays on the slab go with the tool in hand
    if (gs.tool !== surfTool) { surfTool = gs.tool; surfDirty = true; }
    surfWait -= dt;
    if (surfDirty && surfWait <= 0) { surfWait = 0.1; refreshSurface(); }
    if (playing && !modalOpen) updateEffects(dt);
    if (playing) netTick(dt);
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
  }

  // ------------------------------------------------------------------ dirt
  // Concrete sticks. A tool that works in it collects grey splats, more the longer it works; they
  // come off at the water tank, and left long enough they're part of the tool.
  // Every tool that can end up in wet concrete: the finishing tools by trade, the shovel by the
  // shovelful, and the hammer, the pliers and the cutter when a job has them in the pour. (The hose
  // is the pump's: the pump driver washes his own.)
  const DIRTY_TOOLS = ['float', 'handTrowel', 'shovel', 'hammer', 'pliers', 'cutter', 'trowelSmall', 'trowelBig', 'rideOn'];
  const dirtSpots = {};
  const dirtShown = {};
  const dirtMat = new THREE.MeshLambertMaterial({ color: 0x9b9d9a });
  const blobGeo = new THREE.SphereGeometry(1, 6, 4);
  function splatter(group, n, size) {
    group.updateMatrixWorld(true);
    const inv = new THREE.Matrix4().copy(group.matrixWorld).invert();
    const meshes = [];
    group.traverse((o) => { if (o.isMesh && o.geometry && o.geometry.attributes.position && o.material !== dirtMat) meshes.push(o); });
    const out = [], v = new THREE.Vector3(), m4 = new THREE.Matrix4();
    for (let k = 0; k < n && meshes.length; k++) {
      const me = pick(meshes), pos = me.geometry.attributes.position;
      v.fromBufferAttribute(pos, irnd(0, pos.count - 1)).applyMatrix4(m4.multiplyMatrices(inv, me.matrixWorld));
      const b = new THREE.Mesh(blobGeo, dirtMat);
      const r = size * rnd(0.6, 1.4);
      b.scale.set(r, r * 0.55, r);
      b.position.copy(v);
      b.visible = false;
      group.add(b);
      out.push(b);
    }
    return out;
  }
  function buildDirt() {
    DIRTY_TOOLS.forEach((id) => {
      const spots = [];
      // big enough to see from where you stand, and on the tool in your hand
      if (lying[id]) spots.push(...splatter(lying[id], 10, 0.045));
      if (viewTools[id]) spots.push(...splatter(viewTools[id], 10, 0.024));
      if (machines[id]) spots.push(...splatter(machines[id].group, 16, 0.08));
      if (id === 'float') spots.push(...splatter(floatTool, 8, 0.045));
      dirtSpots[id] = spots;
    });
  }
  function dirtOf(id) { return (gs.dirt[id] && gs.dirt[id].d) || 0; }
  function addDirt(id, amt) {
    if (!DIRTY_TOOLS.includes(id)) return;
    const r = gs.dirt[id] || (gs.dirt[id] = { d: 0, at: gs.t });
    if (r.d < 0.05) r.at = gs.t;
    r.d = Math.min(1, r.d + amt);
  }
  /** Concrete that has sat on a tool for two and a half hours is staying there. */
  function dirtSet(id) { return dirtOf(id) > 0.15 && gs.t - gs.dirt[id].at > 150; }
  function showDirt() {
    // the shovel wears it as a crust on the blade that grows up it
    const sd = dirtOf('shovel');
    [viewTools.shovel, lying.shovel].forEach((t) => {
      const cr = t && t.traverse && (t.userData.crustRef || (t.userData.crustRef = findCrust(t)));
      if (!cr) return;
      cr.visible = sd > 0.05;
      cr.scale.set(1, clamp(0.35 + sd, 0.35, 1.2), 1);
      cr.position.y = -0.1 * (1 - clamp(0.35 + sd, 0.35, 1));
    });
    DIRTY_TOOLS.forEach((id) => {
      const spots = dirtSpots[id];
      if (!spots) return;
      const n = Math.ceil(dirtOf(id) * spots.length);
      if (dirtShown[id] === n) return;
      dirtShown[id] = n;
      // the lying and the in-hand copies each show the same share of theirs
      spots.forEach((b, k) => { b.visible = (k % 8) < Math.ceil(dirtOf(id) * 8); });
    });
  }
  function findCrust(root) { let c = null; root.traverse((o) => { if (!c && o.userData && o.userData.crust) c = o.userData.crust; }); return c; }
  function dirtyTools() { return DIRTY_TOOLS.filter((id) => gs.tools[id] && dirtOf(id) > 0.15); }
  /** Where the tools are that aren't back at the van. */
  function toolsOut() {
    return TOOL_IDS.filter((id) => {
      const t = gs.tools[id];
      if (!t || id === 'hose' || t.in === 'van' || t.in === 'gone') return false;
      if (t.in === 'hand') return !atVan(player.x, player.z);
      return !atVan(t.x, t.z);
    });
  }
  const theList = (ids) => { const n = ids.map((id) => TOOLS[id].the); return n.length > 1 ? n.slice(0, -1).join(', ') + ' and ' + n[n.length - 1] : n[0] || ''; };

  // ------------------------------------------------------------------ breakages
  // Things break: a machine's gearbox gives up, a hammer loses its head, a float pole folds. There's
  // a spare hand tool in the van; a spare machine comes out from the yard later. All of it is paid for.
  function charge(label, eur) {
    if (isGuest()) { netSend({ t: 'charge', label: `${label} (${net.name})`, eur }); return; }
    gs.charges.push([label, eur]);
  }
  function breakHandTool(id, line, eur) {
    if (gs.broken[id + 'Once']) return false;
    gs.broken[id + 'Once'] = true;
    if (gs.tool === id) gs.tool = 'hands';
    const [x, z, yaw] = TOOL_HOME[id];
    gs.tools[id] = { in: 'ground', x, z, yaw };
    gs.dirt[id] = { d: 0, at: gs.t };
    sfx('snip'); sfx('clank');
    for (let k = 0; k < 10; k++) emit(player.x + rnd(-0.3, 0.3), 1.0, player.z + rnd(-0.3, 0.3), rnd(-1.5, 1.5), rnd(0.5, 2), rnd(-1.5, 1.5), 0.6, 0x8a6a3a, 0.04);
    toast(`${line} There's a spare in the van. It goes on the bill.`, 'warn');
    charge(`Broken: ${TOOLS[id].name.toLowerCase()}`, eur);
    remember(line);
    return true;
  }
  function breakMachine(id) {
    gs.broken[id] = true;
    const t = gs.tools[id];
    sfx('thud', t.x, t.z); sfx('buzz', t.x, t.z);
    for (let k = 0; k < 30; k++) emit(t.x + rnd(-0.3, 0.3), 0.7, t.z + rnd(-0.3, 0.3), rnd(-0.3, 0.3), rnd(0.8, 1.8), rnd(-0.3, 0.3), 2.2, 0x3a3c3f, 0.18, -0.1);
    const line = fresh(L.machineDies).replace('{m}', TOOLS[id].name.toLowerCase());
    toast(line, 'warn');
    say(line, 'me');
    charge(`${TOOLS[id].name}: gearbox, replaced`, id === 'rideOn' ? 420 : 180);
    remember(`The ${TOOLS[id].name.toLowerCase()} died under you.`);
    if (isGuest()) netSend({ t: 'repair', id });
    at(gs.t + irnd(35, 55), () => {
      gs.broken[id] = false;
      gs.fit[id] = 'pans';
      toast(`The yard drops off a spare ${TOOLS[id].name.toLowerCase()}. It's older than you and louder than the pump. Pans on it.`, 'good');
    });
  }

  // ------------------------------------------------------------------ an inside day: the new hall
  // Some slabs go down inside a hall that is already up: two long walls, columns, steel trusses and
  // clear roof sheets. Both ends stay open, the van's and the pump's.
  const hall = new THREE.Group();
  hall.visible = false;
  scene.add(hall);
  const hallWalls = [];
  function buildHall() {
    hall.traverse((o) => { if (o.geometry) o.geometry.dispose(); });
    hall.clear();
    hallWalls.length = 0;
    hall.visible = !!day.indoor;
    if (!day.indoor) return;
    const b = site.box, m = 1.4, H = 4.2;
    const x0 = b.x0 - m, x1 = b.x1 + m, z0 = b.z0 - m, z1 = b.z1 + m;
    const len = x1 - x0, cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
    [z0, z1].forEach((z) => {
      const w = box(len, H, 0.25, 0xc9c4ba, cx, H / 2, z, hall);
      w.receiveShadow = true; w.castShadow = true;
      hallWalls.push({ cx, cz: z, hx: len / 2, hz: 0.13, ang: 0 });
    });
    const n = Math.max(2, Math.round(len / 3.2));
    for (let k = 0; k <= n; k++) {
      const x = x0 + (len * k) / n;
      [z0, z1].forEach((z) => box(0.3, H + 0.3, 0.34, 0x5a5f64, x, (H + 0.3) / 2, z, hall));
      box(0.16, 0.5, z1 - z0, 0x6c7176, x, H + 0.4, cz, hall).castShadow = true;
      if (k < n) box(0.9, 0.07, 0.22, new THREE.MeshBasicMaterial({ color: 0xfff6dc }), x + len / n / 2, H + 0.1, cz, hall);
    }
    box(len + 0.6, 0.04, z1 - z0 + 0.6, new THREE.MeshLambertMaterial({ color: 0xdfe8ee, transparent: true, opacity: 0.25, depthWrite: false }), cx, H + 0.68, cz, hall);
  }

  // ------------------------------------------------------------------ the yard, a new layout every day
  // The van, the water tank, the kebab stand, the loo and the site office go somewhere new each day,
  // and so do the pump and the line of pipes it lays to the slab.
  const layoutObs = [];              // what you walk round: centre, half sizes, turn, and its colour on the map
  const turnXZ = (x, z, a) => [x * Math.cos(a) + z * Math.sin(a), -x * Math.sin(a) + z * Math.cos(a)];
  /** A spot given in the van's own frame: x forward (the back is -x), z across. */
  function vanPoint(lx, lz) { const [x, z] = turnXZ(lx, lz, van.rotation.y); return P(van.position.x + x, van.position.z + z); }
  function vanLocal(x, z) { return turnXZ(x - van.position.x, z - van.position.z, -van.rotation.y); }
  /** The height of the ramp at the back of the van, where it's down; 0 anywhere else. */
  function rampY(x, z) {
    if (vanBack.open < 0.99) return 0;
    const [lx, lz] = vanLocal(x, z);
    if (lx > RAMP_TOP.x || lx < RAMP_TOP.x - RAMP_RUN || Math.abs(lz) > 0.8) return 0;
    return RAMP_TOP.z * (lx - (RAMP_TOP.x - RAMP_RUN)) / RAMP_RUN + 0.03;
  }
  /** At the back of the van: on the ramp or on the ground round its foot. */
  function atVan(x, z) {
    const [lx, lz] = vanLocal(x, z);
    // behind it and a little to either side — the ride-on parks beside the ramp, 3.6 m off the middle
    return lx < -2.4 && lx > -8 && Math.abs(lz) < 4.4;
  }
  function boxDist(x, z) {
    const dx = Math.max(SLAB.x0 - x, 0, x - SLAB.x1), dz = Math.max(SLAB.z0 - z, 0, z - SLAB.z1);
    return Math.hypot(dx, dz);
  }
  function placeLayout() {
    layoutObs.length = 0;
    const free = (x, z, r) => boxDist(x, z) > r + 2.5 && !(x > 5 && Math.abs(z) < 15) && Math.abs(x) < 41 - r && Math.abs(z) < 31 - r
      && layoutObs.every((o) => hyp(o.cx, o.cz, x, z) > o.r + r + 1.5);
    const find = (gen, r) => { for (let k = 0; k < 500; k++) { const q = gen(); if (free(q.x, q.z, r)) return q; } return gen(); };
    const put = (obj, q, yaw, hx, hz, col, r, ox) => {
      obj.position.set(q.x, 0, q.z);
      obj.rotation.y = yaw;
      const [cx, cz] = turnXZ(ox || 0, 0, yaw);
      layoutObs.push({ cx: q.x + cx, cz: q.z + cz, hx, hz, ang: yaw, col, r });
    };
    const facing = (q) => Math.atan2(site0.x - q.x, site0.z - q.z);
    const site0 = P((SLAB.x0 + SLAB.x1) / 2, 0);
    // the van, with its back to the slab: west of it, or north, or south
    const side = day.indoor ? 'w' : weighted([[0.5, 'w'], [0.25, 'n'], [0.25, 's']]);
    const vq = side === 'w' ? P(rnd(-31, -26), rnd(-9, 9)) : P(rnd(-17, -1), side === 'n' ? rnd(-23, -18) : rnd(18, 23));
    const vy = (side === 'w' ? Math.PI : side === 'n' ? Math.PI / 2 : -Math.PI / 2) + rnd(-0.3, 0.3);
    put(van, vq, vy, 3.05, 1.15, '#e9e7e2', 7.5, 0.35);
    // what the back of the van puts where
    const vp = (lx, lz) => vanPoint(lx, lz);
    Object.assign(POS.vanDoor, vp(-6.4, 0));
    Object.assign(POS.vanSide, vp(-4.3, 1.75));
    Object.assign(POS.vanSeat, vp(1.4, -1.9));      // the driver's door: the spare clothes are behind the seat
    Object.assign(POS.vanCorner, vp(3.9, 1.6));     // the front corner, away from the road. Mostly.
    const home = {
      handTrowel: [-3.35, -0.35, 0.2], hammer: [-3.85, 0.3, -0.3], pliers: [-4.35, -0.3, 0.5], cutter: [-4.85, 0.3, 0.1],
      float: [-4.6, 1.45, 0], shovel: [-4.7, -1.45, 0.1],
      trowelSmall: [-6.2, 1.9, 0.4], trowelBig: [-6.3, -2.0, -0.3], rideOn: [-3.5, 3.6, 0],
    };
    Object.entries(home).forEach(([id, [lx, lz, yaw]]) => { const q = vp(lx, lz); TOOL_HOME[id] = [q.x, q.z, van.rotation.y + yaw]; });
    const lq = vp(1.0, side === 'w' ? -3.2 : 3.2);
    lampPole.position.set(lq.x, 4, lq.z); lamp.position.set(lq.x, 8, lq.z); flood.position.set(lq.x, 8, lq.z);
    Object.assign(VAN_IN, vp(-2.3, 0)); VAN_IN.y = 1.3;
    Object.assign(VAN_THROW, vp(-10.5, 1.4));
    // the water tank, handy but not in the way
    const iq = find(() => P(rnd(-28, 4), rnd(-19, 19)), 1.5);
    put(ibc, iq, facing(iq), 0.65, 0.65, '#cfe3ea', 1.5);
    const iy = facing(iq);
    Object.assign(POS.ibc, iq); Object.assign(POS.ibcFront, P(iq.x + Math.sin(iy) * 1.45, iq.z + Math.cos(iy) * 1.45));
    // the loo, a little way off, its door to the site
    const lo = find(() => P(rnd(-38, 2), rnd(-28, 28)), 1.2);
    put(loo, lo, facing(lo), 0.6, 0.6, '#2c6ad6', 1.2);
    const ly = facing(lo);
    Object.assign(POS.loo, lo); Object.assign(POS.looFront, P(lo.x + Math.sin(ly) * 1.3, lo.z + Math.cos(ly) * 1.3));
    // the site office and the kebab stand, further out
    const oq = find(() => P(rnd(-39, 0), rnd(-29, 29)), 4);
    put(office, oq, facing(oq) + Math.PI / 2 + rnd(-0.2, 0.2), 3.1, 1.3, '#3c6e9e', 4);
    Object.assign(POS.office, oq);
    const kq = find(() => (chance(0.5) ? P(rnd(-40, -30), rnd(-29, 29)) : P(rnd(-36, 30), pick([-1, 1]) * rnd(24, 29))), 3);
    const ky = facing(kq);
    put(kiosk, kq, ky, 1.7, 1.4, '#2f6f6a', 3);
    Object.assign(POS.kiosk, kq); Object.assign(POS.kioskFront, P(kq.x + Math.sin(ky) * 2.6, kq.z + Math.cos(ky) * 2.6));
    // the clutter, round the edges of the yard, out of the way of everything that moves
    props.forEach(([g, r, hx, hz, col]) => {
      const q = find(() => (chance(0.5) ? P(rnd(-40, 36), pick([-1, 1]) * rnd(22, 30)) : P(pick([-1, 1]) * rnd(30, 40), rnd(-28, 28))), r);
      put(g, q, rnd(0, Math.PI * 2), hx, hz, col, r);
    });
    // the pump parks east of the slab, either side of the gate; its pipes find their own way in
    const ez = gz(ENTRY.j) + 0.5;
    const pz = pick([-1, 1]) * rnd(1.5, 6.5);
    Object.assign(POS.pump, P(weighted([[0.35, () => rnd(17.5, 21)], [0.4, () => rnd(21, 26)], [0.25, () => rnd(26, 30)]])(), pz));
    Object.assign(POS.pumpOut, P(POS.pump.x - 3.6, pz + 0.4));
    // the mixer backs up to the pump, chute over the hopper at its rear
    Object.assign(POS.mixer, P(POS.pump.x + 9.0, pz));
    Object.assign(POS.pile, P(POS.pump.x - 5.5, pz < 0 ? pz - 3.2 : pz + 3.2));
    pile.position.set(POS.pile.x, 0, POS.pile.z);
    const a = POS.pumpOut, e = P(SLAB.x1 + 0.4, ez + 0.3);
    const len = hyp(a.x, a.z, e.x, e.z), bend = rnd(-3, 3) * Math.min(1.6, len / 12);
    const c = P((a.x + e.x) / 2 - ((e.z - a.z) / len) * bend, (a.z + e.z) / 2 + ((e.x - a.x) / len) * bend);
    PIPE_N = clamp(Math.round((len + Math.abs(bend) * 0.5) / 2.6), 3, 9);
    PIPE_ROUTE.length = PIPE_N;
    for (let k = 1; k <= PIPE_N; k++) {
      const t = k / PIPE_N, u = 1 - t;
      PIPE_ROUTE[k - 1] = P(u * u * a.x + 2 * u * t * c.x + t * t * e.x, u * u * a.z + 2 * u * t * c.z + t * t * e.z);
    }
    BOOM_AT.z = clamp(ez - 4, -8, 2);
  }

  // ------------------------------------------------------------------ playing together
  // Up to four phones on one site, phone to phone (the app carries the messages over Nearby). One
  // hosts: it runs the clock, the trucks, the pump, the dog and the pay slip, and tells the others
  // how things stand five times a second. Everyone works the same slab: whoever pours, floats or
  // trowels a square changes it for everybody, a job done by one is done for all, and a tool in one
  // pair of hands isn't in anybody else's. The day itself grows from one number, the seed, on every
  // phone alike, so only what changes has to travel.
  const NET_VER = 1;
  const CREW_COLOURS = [0xff7a1a, 0x3ec1ff, 0xd4f53c, 0xff4fa3];
  const net = {
    role: 'solo', me: 'H', name: '', hostId: '', hostName: '', seed: 0, started: false,
    peers: new Map(), found: new Map(), crew: new Map(), joined: 0,
    lastT: 0, meAt: 0, diffAt: 0, snapAt: 0,
    shadowCells: [], shadowTools: {}, paintOut: [], pourM3: 0, wasteM3: 0,
    remoteWalkers: new Map(), remoteHelper: null,
  };
  let netRemote = false;          // a co-worker's job being played out here: no toasts, no voices
  let netCapture = null;          // toasts collected to send back to whoever asked
  function isGuest() { return net.role === 'guest'; }
  function isHost() { return net.role === 'host'; }
  function inTeam() { return net.started && (net.role === 'host' || net.role === 'guest'); }
  const canNet = () => !!(appBridge && typeof appBridge.netHost === 'function');
  const appVersion = () => { try { return appBridge && appBridge.version ? String(appBridge.version()) : 'web'; } catch (e) { return 'web'; } };
  function netSend(o) { try { appBridge.netSend(JSON.stringify(o)); } catch (e) { /* no link */ } }
  function netSendTo(id, o) { try { appBridge.netSendTo(id, JSON.stringify(o)); } catch (e) { /* no link */ } }
  /** From the host: to every co-worker but the one it came from. */
  function netRelay(from, o) { net.peers.forEach((p, id) => { if (id !== from) netSendTo(id, o); }); }
  const r2 = (v) => Math.round(v * 100) / 100;
  const r4 = (v) => Math.round(v * 10000) / 10000;
  function crewName(id) { if (id === net.me) return net.name || 'You'; const c = net.crew.get(id); return c ? c.name : id === 'H' ? net.hostName || 'The host' : 'Somebody'; }

  // ---------------- one day, grown from one number on every phone
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function seeded(seed, fn) {
    const orig = Math.random;
    Math.random = mulberry32(seed);
    try { return fn(); } finally { Math.random = orig; }
  }

  // ---------------- the lobby, on the title card
  function lobbyStatus(text) { $('#lobbyStatus').textContent = text; }
  function crewNames() { return [net.role === 'host' ? net.name : net.hostName].concat([...net.crew.values()].filter((c) => c.id !== 'H').map((c) => c.name)); }
  function lobbyList() {
    const box = $('#lobbyList');
    box.innerHTML = '';
    if (net.role === 'joining') {
      net.found.forEach((name, id) => {
        const b = document.createElement('button');
        b.textContent = `Join ${name}'s day`;
        b.addEventListener('click', () => { lobbyStatus(`Knocking on ${name}'s van…`); appBridge.netConnect(id); });
        box.appendChild(b);
      });
    }
    if (net.role === 'host' || net.role === 'guest') {
      const d = document.createElement('div');
      d.className = 'crew';
      d.textContent = 'Crew: ' + crewNames().join(', ');
      box.appendChild(d);
    }
    titleButtons();
  }
  function titleButtons() {
    const waiting = net.role === 'guest' || net.role === 'joining';
    const start = $('#btnStart');
    start.disabled = waiting;
    start.textContent = net.role === 'guest' ? `Waiting for ${net.hostName}…` : net.role === 'joining' ? 'Pick a day to join' : isHost() ? `Clock in, crew of ${net.crew.size + 1}` : 'Clock in';
    $('#btnReroll').hidden = waiting;
    $('#btnLeave').hidden = net.role === 'solo';
    $('#btnHost').disabled = $('#btnJoin').disabled = net.role !== 'solo';
  }
  function myName() {
    const v = ($('#lobbyName').value || '').trim().slice(0, 16) || 'Worker';
    store('pourday.name', v);
    return v;
  }
  function hostDay() {
    net.role = 'host'; net.me = 'H'; net.name = myName(); net.crew.clear(); net.peers.clear(); net.joined = 0;
    lobbyStatus('Asking the phone for "Nearby devices"…');
    appBridge.netHost(net.name);
    showTitle();
    lobbyList();
  }
  function joinDay() {
    net.role = 'joining'; net.name = myName(); net.found.clear(); net.crew.clear(); net.peers.clear();
    lobbyStatus('Asking the phone for "Nearby devices"…');
    appBridge.netJoin(net.name);
    lobbyList();
  }
  /** Back to playing alone, from anywhere: the lobby, a shared day, or a host who has gone home. */
  function leaveCrew(why) {
    if (isHost()) netSend({ t: 'bye' });
    try { if (canNet()) appBridge.netLeave(); } catch (e) { /* already gone */ }
    net.role = 'solo'; net.me = 'H'; net.started = false; net.hostId = ''; net.hostName = '';
    net.peers.clear(); net.found.clear();
    net.crew.forEach((c) => { if (c.m) scene.remove(c.m); if (c.stream) scene.remove(c.stream); });
    net.crew.clear();
    net.remoteWalkers.forEach((w) => scene.remove(w.m));
    net.remoteWalkers.clear();
    if (net.remoteHelper) { scene.remove(net.remoteHelper); net.remoteHelper = null; }
    if (gs.phase !== 'title') { closeAllModals(); resetWorld(); }
    showTitle();
    lobbyStatus(why || 'One phone hosts the day; the others join it. Up to four of you, within shouting distance.');
    lobbyList();
    if (why) $('#lobby').hidden = false;
  }
  function closeAllModals() { modalQueue.length = 0; if (modalOpen) { $('#modal').hidden = true; modalOpen = null; } }
  function sendLobby() {
    if (!isHost()) return;
    netSend({ t: 'lobby', seed: net.seed, host: net.name, hostMm: inMixMaster ? 1 : 0, crew: [...net.crew.values()].map((c) => [c.id, c.name, c.mm ? 1 : 0]) });
    lobbyList();
  }
  /** Clock in: the same for everybody, the day from the seed and its jobs from the next one. */
  function beginDay() {
    audioStart();
    resetWorld();
    seeded(net.seed + 1, () => { gs = freshState(); buildSite(); });
    cellsDirty = true;
    buildLateMarkers();
    startDay();
    if (net.role === 'host' || net.role === 'guest') {
      net.started = true;
      net.shadowCells = gs.grid.map(cellKey);
      net.shadowTools = {};
      net.paintOut.length = 0;
      net.lastT = 0;
    }
  }

  // ---------------- what the app hears
  window.pdNet = { onEvent(json) { let e; try { e = JSON.parse(json); } catch (x) { return; } netEvent(e); } };
  function netEvent(e) {
    switch (e.t) {
      case 'hosting': lobbyStatus('Your day is open. Co-workers join from their own phones: Play together, then Join a day.'); break;
      case 'searching': lobbyStatus('Looking for a day nearby… Bluetooth on, and stand close.'); break;
      case 'found': if (net.role === 'joining' && net.found.get(e.id) !== e.name) { net.found.set(e.id, e.name); lobbyStatus('Found one. Tap to join:'); lobbyList(); } break;
      case 'lost': net.found.delete(e.id); lobbyList(); break;
      case 'denied': leaveCrew(`Without "Nearby devices" the phones can't find each other. It can be allowed for ${appName || 'the app'} in the phone's settings.`); break;
      case 'error': leaveCrew(`That didn't work (${e.text}). Is Bluetooth on? Location too, on older phones.`); break;
      case 'failed': lobbyStatus(`Couldn't get through to ${e.name}. Try again, closer.`); break;
      case 'connected':
        if (net.role === 'host') { net.peers.set(e.id, { name: e.name }); lobbyStatus(`${e.name} is coming…`); }
        else {
          net.role = 'guest'; net.hostId = e.id; net.hostName = e.name;
          net.peers.set(e.id, { name: e.name });
          netSendTo(e.id, { t: 'hello', name: net.name, ver: NET_VER, app: appVersion(), mm: inMixMaster ? 1 : 0 });
          lobbyStatus(`On ${e.name}'s site. Waiting for ${e.name} to clock in.`);
          lobbyList();
        }
        break;
      case 'disconnected':
        if (isGuest() || net.role === 'joining') { if (e.id === net.hostId) leaveCrew(`${net.hostName}'s phone left the site. The day is over for you.`); }
        else if (isHost()) crewGone(e.id);
        break;
      case 'msg': { let m; try { m = JSON.parse(e.data); } catch (x) { return; } netMsg(e.id, m); break; }
      default: break;
    }
  }
  function netMsg(from, m) {
    switch (m.t) {
      // at the host
      case 'hello': {
        // the same game on both phones, whichever app carries it: MixMaster or Pour Day on its own
        if (m.ver !== NET_VER || m.app !== appVersion()) { netSendTo(from, { t: 'nope', why: `a different version of the game on the two phones. Update both to the newest.` }); return; }
        if (net.started) { netSendTo(from, { t: 'nope', why: `${net.name} has already clocked in. Join the next day.` }); return; }
        const c = crewMember(from, m.name);
        c.mm = !!m.mm;
        c.colour = CREW_COLOURS[++net.joined % CREW_COLOURS.length];
        netSendTo(from, { t: 'welcome', you: from });
        sendLobby();
        lobbyStatus(`${m.name} is on your site.`);
        break;
      }
      case 'me': { const c = net.crew.get(from); if (c) crewUpdate(c, m); break; }
      case 'pour': hostPour(m.m3 || 0, m.w || 0); break;
      case 'charge': gs.charges.push([m.label, m.eur]); break;
      case 'repair': at(gs.t + irnd(35, 55), () => { gs.broken[m.id] = false; gs.fit[m.id] = 'pans'; }); break;
      case 'finish': if (gs.phase === 'pour') { toast(`${crewName(from)} says the pour is done.`); finishPour(); } break;
      case 'shout': hostShout(from, m.wid); break;
      // either way
      case 'cells':
        applyCells(m);
        if (isHost()) netRelay(from, Object.assign({}, m, { from }));
        break;
      case 'tools':
        applyTools(m.d, isHost() ? from : m.from);
        if (isHost()) netRelay(from, { t: 'tools', d: m.d, from });
        break;
      case 'mk':
        remoteMarker(m.id, isHost() ? from : m.from);
        if (isHost()) netRelay(from, { t: 'mk', id: m.id, from });
        break;
      case 'poke':
        if (isHost() && m.to !== 'H') netSendTo(m.to, Object.assign({}, m, { from }));
        else pokeReceived(isHost() ? from : m.from, m.kind);
        break;
      // at a co-worker
      case 'welcome': net.me = m.you; break;
      case 'nope': leaveCrew(`Can't join: ${m.why}`); break;
      case 'lobby':
        net.hostName = m.host;
        net.hostMm = !!m.hostMm;
        net.crew.clear();
        m.crew.forEach(([id, name, mm]) => { if (id !== net.me) crewMember(id, name).mm = !!mm; });
        crewMember('H', m.host).mm = net.hostMm;
        if (gs.phase !== 'title') { closeAllModals(); resetWorld(); }
        net.started = false;
        showTitle(m.seed);
        lobbyStatus(`On ${m.host}'s site. Waiting for ${m.host} to clock in.`);
        lobbyList();
        break;
      case 'start': beginDay(); break;
      case 'snap': applySnap(m); break;
      case 'note': toast(`${m.who ? m.who + ': ' : ''}${m.title}`, 'warn'); if (m.voice) say(m.say || m.text, m.voice); break;
      case 'toast': toast(m.text, m.kind); break;
      case 'end': guestEnd(m); break;
      case 'bye': leaveCrew(`${net.hostName} went home. The day is over for you.`); break;
      default: break;
    }
  }

  // ---------------- the crew: the other people on the site
  function crewMember(id, name) {
    let c = net.crew.get(id);
    if (!c) {
      c = { id, name, x: 0, z: 0, yaw: 0, tx: 0, tz: 0, tool: 'hands', act: '', ax: 0, az: 0, flip: 0, prints: 0, falls: 0, flips: 0, colour: id === 'H' ? CREW_COLOURS[0] : CREW_COLOURS[(net.crew.size + 1) % CREW_COLOURS.length], m: null, stream: null, seen: 0, phase: 0 };
      net.crew.set(id, c);
    }
    c.name = name;
    return c;
  }
  function crewUpdate(c, s) {
    c.tx = s.x; c.tz = s.z; c.yaw = s.yaw; c.tool = s.tool; c.act = s.act || ''; c.ax = s.ax; c.az = s.az; c.flip = s.fl || 0;
    c.prints = s.pr || 0; c.falls = s.fa || 0; c.flips = s.fp || 0; c.seen = performance.now(); c.boots = s.bt || 0;
    if (!c.m) { c.x = s.x; c.z = s.z; }
  }
  function crewMesh(c) {
    const m = makePerson({ vest: c.colour, hat: 'hard', hatColor: 0xf2f0ea, g: 'm', shirt: 0x3b3f45, logo: !!c.mm });
    m.traverse((o) => { if (o.isMesh) o.castShadow = true; });
    const tag = textSprite(c.name, { w: 1.5, color: '#fff3e6' });
    tag.position.y = 2.3;
    m.add(tag);
    scene.add(m);
    c.m = m;
    c.stream = cyl(0.05, 0.05, 1, 0x7d7f80, 0, 0, 0, scene, 8);
    c.stream.visible = false;
  }
  function crewGone(id) {
    const c = net.crew.get(id);
    net.peers.delete(id);
    if (!c) return;
    // whatever was in their hands stays where they stood
    Object.entries(gs.tools || {}).forEach(([tid, t]) => { if (t.in === 'hand' && t.by === id) gs.tools[tid] = { in: 'ground', x: c.x, z: c.z, yaw: c.yaw }; });
    if (c.m) scene.remove(c.m);
    if (c.stream) scene.remove(c.stream);
    net.crew.delete(id);
    toast(`${c.name} left the site.`, 'warn');
    if (!net.started) sendLobby();
  }
  function crewActing(id, kind) { const c = net.crew.get(id); return !!(c && c.act === kind); }
  function updateCrew(dt) {
    net.crew.forEach((c) => {
      if (c.id === net.me) return;
      if (!c.m) crewMesh(c);
      const p = c.m.position, u = c.m.userData;
      const k = 1 - Math.exp(-dt * 9);
      const ox = p.x, oz = p.z;
      c.x = lerp(c.x, c.tx, k); c.z = lerp(c.z, c.tz, k);
      p.set(c.x, onSlab(c.x, c.z) && gs.phase === 'pour' ? groundY(c.x, c.z) * 0.4 : groundY(c.x, c.z), c.z);
      c.m.rotation.y = turnTo(c.m.rotation.y, c.yaw + Math.PI, k);
      const moved = hyp(p.x, p.z, ox, oz);
      c.phase += moved * 4.5;
      if (moved > 0.004) { u.legL.rotation.x = Math.sin(c.phase) * 0.55; u.legR.rotation.x = -Math.sin(c.phase) * 0.55; }
      else { u.legL.rotation.x *= 0.85; u.legR.rotation.x *= 0.85; }
      if (u.bootCrust) u.bootCrust.forEach((b) => { b.visible = !!c.boots; });
      const working = c.act === 'pour' || c.act === 'level' || c.act === 'repair' || c.act === 'shovel' || c.act === 'trowel' || c.act === 'marker';
      u.armR.rotation.x = lerp(u.armR.rotation.x, c.flip ? -1.75 : working ? -1.1 + Math.sin(toolT * 6) * 0.25 : c.tool !== 'hands' ? -0.5 : 0, k);
      const fg = u.armR.userData.finger;
      fg.visible = !!c.flip && u.armR.rotation.x < -1.2; fg.rotation.x = -u.armR.rotation.x;
      u.armL.rotation.x = lerp(u.armL.rotation.x, working ? -0.9 : 0, k);
      // concrete out of the hose they're holding
      const pour = c.act === 'pour' && gs.tools.hose && gs.tools.hose.by === c.id;
      c.stream.visible = pour;
      if (pour) stretch(c.stream, new THREE.Vector3(c.x - Math.sin(c.yaw) * 0.5, 1.0, c.z - Math.cos(c.yaw) * 0.5), new THREE.Vector3(c.ax, surfY(fillAt(c.ax, c.az)), c.az));
    });
  }
  function myState() {
    const acting = input.action && lastCtxKind && lastCtxKind !== 'none' ? lastCtxKind : '';
    return { bt: (gs.boots || 0) > 0.05 ? 1 : 0, x: r2(player.x), z: r2(player.z), yaw: r2(player.yaw), tool: gs.tool, act: acting, ax: target ? r2(target._hx) : pourOut ? r2(pourOut.x) : 0, az: target ? r2(target._hz) : pourOut ? r2(pourOut.z) : 0, fl: flipT > 0 ? 1 : 0, pr: gs.stats.prints, fa: gs.stats.falls, fp: gs.stats.flips };
  }
  function pokeReceived(fromId, kind) {
    const name = crewName(fromId);
    if (kind === 'flip') { toast(`${name} gives you the finger. ${fresh(L.crewFlip)}`, 'warn'); return; }
    const l = fresh(L.crewShout);
    toast(`${name}: ${l}`, 'warn');
    say(l, crewVoice(fromId));
  }

  /** A co-worker's voice: theirs for the day, a man's, and nobody else's while there are voices to go round. */
  function crewVoice(id) {
    const c = net.crew.get(id);
    if (!c) return { p: 1, r: 1.05, key: 'crew' + id, g: 'm' };
    if (!c.voice) c.voice = { p: rnd(0.86, 1.12), r: rnd(0.96, 1.12), key: 'crew' + id, g: 'm', name: c.name, at: () => c.m && c.m.position };
    return c.voice;
  }

  // ---------------- the slab: every square anybody changed, and what their tools drew on it
  function cellKey(c) {
    let h = 0;
    for (const m of c.marks) h = (h * 31 + Math.round(m.depth * 100) + Math.round(m.x * 10)) | 0;
    return `${Math.round(c.fill * 2)}|${c.covP ? 1 : 0}${c.covB ? 1 : 0}${c.defect ? 1 : 0}${c.edged}|${c.pan}|${c.blade}|${c.marks.length}|${h}|${c.load}`;
  }
  function cellRec(c) {
    // the flags: pan, blade, set-in, then the edged sides four bits up
    return [c.idx, Math.round(c.fill * 2) / 2, (c.covP ? 1 : 0) | (c.covB ? 2 : 0) | (c.defect ? 4 : 0) | ((c.edged || 0) << 3), c.pan, c.blade,
      c.marks.map((m) => [m.kind, r2(m.x), r2(m.z), r2(m.rot), r2(m.depth), m.ch || '']), c.load];
  }
  function applyCells(msg) {
    (msg.d || []).forEach((r) => {
      const c = gs.grid[r[0]];
      if (!c) return;
      c.fill = r[1]; c.covP = !!(r[2] & 1); c.covB = !!(r[2] & 2); c.defect = !!(r[2] & 4); c.edged = (r[2] >> 3) & 15; c.pan = r[3]; c.blade = r[4];
      c.marks = r[5].map(([kind, x, z, rot, depth, ch]) => (ch ? { kind, x, z, rot, depth, ch } : { kind, x, z, rot, depth }));
      if (r[6] !== undefined) c.load = r[6];
      net.shadowCells[c.idx] = cellKey(c);
    });
    if ((msg.d || []).length) { cellsDirty = true; surfDirty = true; }
    netRemote = true;
    try { (msg.p || []).forEach(([kind, x, z, a]) => { const f = PAINTS[kind]; if (f) f(x, z, a || undefined); }); } finally { netRemote = false; }
  }
  const PAINTS = { pour: paintPour, float: paintFloat, pan: paintPan, blade: paintBlade, fibres: paintFibres, gouge: paintGouge };
  function netPaint(kind, x, z, a) { if (inTeam() && !netRemote && net.paintOut.length < 400) net.paintOut.push([kind, r2(x), r2(z), a === undefined ? 0 : r2(a)]); }
  function toolRec(id) {
    const t = gs.tools[id];
    return [t.in, r2(t.x || 0), r2(t.z || 0), r2(t.yaw || 0), t.by || '', gs.fit[id] || '', r2(dirtOf(id)), gs.broken[id] ? 1 : 0];
  }
  function applyTools(d, from) {
    Object.entries(d || {}).forEach(([id, r]) => {
      const [inn, x, z, yaw, by0, fit, dirt, br] = r;
      const by = by0 || (inn === 'hand' ? (from === undefined ? '' : from) : '');
      const now = gs.tools[id];
      // two hands on one tool: whoever had it first keeps it
      if (isHost() && inn === 'hand' && now && now.in === 'hand' && now.by && now.by !== by) { netSendTo(from, { t: 'tools', d: { [id]: toolRec(id) } }); return; }
      const mine = gs.tool === id;
      gs.tools[id] = { in: inn, x, z, yaw };
      if (by) gs.tools[id].by = by;
      if (fit) gs.fit[id] = fit;
      gs.dirt[id] = { d: dirt, at: (gs.dirt[id] && gs.dirt[id].d > 0.05 && gs.dirt[id].at) || gs.t };
      gs.broken[id] = !!br;
      net.shadowTools[id] = JSON.stringify(toolRec(id));
      if (mine && !(inn === 'hand' && by === net.me)) { gs.tool = 'hands'; toast(`${crewName(by)} has the ${TOOLS[id].name.toLowerCase()} now.`, 'warn'); }
    });
  }
  function sendDiffs() {
    const cells = [];
    for (const c of gs.cells) { const k = cellKey(c); if (net.shadowCells[c.idx] !== k) { net.shadowCells[c.idx] = k; cells.push(cellRec(c)); } }
    const paint = net.paintOut.splice(0, 120);
    // in pieces a phone-to-phone message can carry
    let chunk = [], size = 0;
    const flush = (p) => {
      const o = { t: 'cells', d: chunk, p: p || [] };
      if (isHost()) o.from = 'H';
      netSend(o);
      chunk = []; size = 0;
    };
    cells.forEach((r) => { const n = JSON.stringify(r).length; if (size + n > 20000) flush(); chunk.push(r); size += n; });
    if (chunk.length || paint.length) flush(paint);
    const tools = {};
    let any = false;
    Object.keys(gs.tools).forEach((id) => { const k = JSON.stringify(toolRec(id)); if (net.shadowTools[id] !== k) { net.shadowTools[id] = k; tools[id] = JSON.parse(k); any = true; } });
    if (any) netSend(isHost() ? { t: 'tools', d: tools, from: 'H' } : { t: 'tools', d: tools });
    if (isGuest() && (net.pourM3 > 0 || net.wasteM3 > 0)) { netSend({ t: 'pour', m3: r4(net.pourM3), w: r4(net.wasteM3) }); net.pourM3 = 0; net.wasteM3 = 0; }
  }
  /** A co-worker's job, done on this phone too: the same flags, none of their hands. */
  const NO_SYNC = new Set(['loo', 'lunch', 'pile', 'home', 'spares', 'behindVan', 'phone']);
  function netMarker(id) {
    if (!inTeam() || NO_SYNC.has(id)) return;
    netSend(isHost() ? { t: 'mk', id, from: 'H' } : { t: 'mk', id });
  }
  function remoteMarker(id, from) {
    const m = markers.find((x) => x.id === id);
    if (!m) return;
    let ready;
    if (/^pipe\d$/.test(id)) ready = gs.pipes === Number(id.slice(4));
    else if (id === 'laserFetch') ready = gs.laserInVan;
    else if (id === 'laser') ready = !gs.laserSetup;
    else if (id === 'laserVan') ready = !gs.laserPacked;
    else if (id === 'laserPack') ready = !gs.laserPacked && tripod.visible;
    else if (id === 'wash') ready = true;
    else ready = m.active();
    if (!ready) return;
    netRemote = true;
    try { m.done(); } finally { netRemote = false; }
    if (!m.active()) m.group.visible = false;
    toastOnce('mk' + id, `${crewName(from)}: ${m.label.replace(/!$/, '')} — done.`, '', 4000);
  }

  // ---------------- at the host
  function hostPour(m3, w) {
    gs.waste += w;
    if (!gs.truck || m3 <= 0) return;
    gs.truck.left -= m3;
    gs.pouredM3 += m3;
    pourTrouble(m3 / 0.11);
    if (gs.truck && gs.truck.left <= 0) truckEmpty();
  }
  function hostShout(from, wid) {
    const w = walkers.find((x) => x.uid === wid);
    if (!w) return;
    netCapture = [];
    try { shoutAt(w); } finally {
      const texts = netCapture;
      netCapture = null;
      texts.forEach(([text, kind]) => netSendTo(from, { t: 'toast', text, kind }));
    }
  }
  /** The passes are the host's to call: when the crew between them has been over nine squares in ten. */
  function hostPassWatch() {
    if (gs.phase !== 'cure' && gs.phase !== 'wash' && gs.phase !== 'pour') return;
    [['covP', 'pan', gs.panPasses], ['covB', 'blade', gs.bladePasses]].forEach(([key, kind, done]) => {
      if (passCoverage(key) < 0.9) return;
      if (done.length >= 3) gs.cells.forEach((c) => { c[key] = false; });
      else completePass(kind);
    });
  }
  function snapshot() {
    const done = (arr) => arr.map((t) => (t.done ? 1 : 0)).join('');
    const crew = [['H', net.name].concat(Object.values(myState()))];
    net.crew.forEach((c) => { if (c.id !== 'H') crew.push([c.id, c.name, c.tx, c.tz, c.yaw, c.tool, c.act, c.ax, c.az, c.flip, c.prints, c.falls, c.flips]); });
    return {
      t: 'snap', tm: r2(gs.t), ph: gs.phase, pz: (modalOpen && modalOpen.who === 'Paused') || settingsOpen ? 1 : 0, H: r2(gs.H),
      ls: gs.laserSetup || 0, lc: (site.levelChecks || []).map((c) => (c.done ? 1 : 0) + (c.fixed ? 2 : 0)).join(''),
      ld: gs.loads.map((l) => (l ? [l.kind, r2(l.h), l.at === null ? -1 : r2(l.at), r4(l.rate), l.fast ? 1 : 0] : 0)), pa: gs.pumpAt, nt: gs.nextTruckAt || 0, ar: gs.arrived, pe: gs.pourEnd,
      fl: [gs.poured, gs.pourStarted, gs.pourDone, gs.washed, gs.gaveUp, gs.laserInVan, gs.laserBattery, gs.laserPacked, gs.pumpHere, gs.pipesGone, gs.prep.unload, gs.prep.laser].map((v) => (v ? 1 : 0)).join(''),
      fm: gs.prep.form.map((v) => (v ? 1 : 0)).join(''),
      pipes: gs.pipes, bl: gs.blocked, bo: gs.blowout ? [gs.blowout.name, r2(gs.blowout.p.x), r2(gs.blowout.p.z)] : 0,
      rb: liftBars.visible ? [r2(liftBars.position.x), r2(liftBars.position.z), gs.rebarDue || 0] : 0,
      tr: gs.truck ? [gs.truck.no, r4(gs.truck.left), gs.truck.waiting ? 1 : 0] : 0, ms: gs.mixState, pm: r4(gs.pouredM3), wa: r4(gs.waste),
      pp: gs.panPasses, bp: gs.bladePasses, ti: done(site.ties), cu: done(site.cuts), ed: done(site.edges), en: gs.edgesDone,
      veh: [pump.visible ? [r2(pump.position.x), r2(pump.position.z)] : 0, mixer.visible ? [r2(mixer.position.x), r2(mixer.position.z)] : 0,
        pumpGuy.visible ? [r2(pumpGuy.position.x), r2(pumpGuy.position.z), r2(pumpGuy.rotation.y)] : 0, pile.visible ? 1 : 0, vanBack.want, tripod.visible ? 1 : 0, performance.now() < rainUntil ? 1 : 0],
      wk: walkers.map((w) => [w.uid, w.kind === 'dog' ? (w.cat ? 'c' : 'd') : 'p', r2(w.m.position.x), r2(w.m.position.z), r2(w.m.rotation.y)]),
      hp: helper.m ? [r2(helper.m.position.x), r2(helper.m.position.z), r2(helper.m.rotation.y), helper.name] : 0,
      uf: ufo.g.visible ? [r2(ufo.g.position.x), r2(ufo.g.position.y), r2(ufo.g.position.z), ufo.beam.visible ? 1 : 0] : 0,
      crew,
    };
  }

  // ---------------- at a co-worker: the day as the host has it
  function applySnap(s) {
    if (!net.started || !gs || gs.phase === 'end') return;
    const dm = s.tm - (net.lastT || s.tm);
    net.lastT = s.tm;
    // the host's pause stops the clock for the whole crew
    if (s.pz && !net.hostPaused) toast(`${net.hostName} stopped the clock. Everything waits for them. As usual.`);
    else if (!s.pz && net.hostPaused) toast(`${net.hostName} is back. The clock runs again.`);
    net.hostPaused = !!s.pz;
    gs.t = s.tm;
    if (gs.phase !== 'morning' || s.ph !== 'prep') gs.phase = s.ph;
    gs.H = s.H; gs.pumpAt = s.pa; gs.nextTruckAt = s.nt; gs.pourEnd = s.pe;
    if (s.ls > (gs.laserSetup || 0)) { gs.laserSetup = s.ls; if (s.ls >= 1 && !gs.laserPacked) tripod.visible = true; }
    if (s.lc && site.levelChecks) s.lc.split('').forEach((v, k) => { const c = site.levelChecks[k]; if (c) { c.done = c.done || !!(v & 1); c.fixed = c.fixed || !!(v & 2); } });
    if (s.ld) s.ld.forEach((l, no) => { if (l) gs.loads[no] = { no, kind: l[0], h: l[1], at: l[2] < 0 ? null : l[2], rate: l[3], fast: !!l[4] }; });
    if (dm > 0 && dm < 240) {
      gs.energy = clamp(gs.energy - 0.04 * dm, 0, 100);
      if (gs.phase !== 'morning') needsTick(dm);
    }
    const f = s.fl.split('').map((c) => c === '1');
    [gs.poured, gs.pourStarted, gs.pourDone, gs.washed, gs.gaveUp, gs.laserInVan, gs.laserBattery, gs.laserPacked, gs.pumpHere] = f;
    if (f[9] && !gs.pipesGone) { gs.pipesGone = 1; pipeGroup.clear(); }
    gs.prep.unload = gs.prep.unload || f[10];
    gs.prep.laser = gs.prep.laser || f[11];
    s.fm.split('').forEach((c, k) => { if (c === '1') gs.prep.form[k] = true; });
    if (gs.pourDone) gs.laserOn = false;
    // the pipe line, a length at a time
    // never past the end of this phone's own line, whatever the host says
    if (!gs.pipesGone) while (gs.pipes < Math.min(s.pipes, PIPE_N)) { const k = gs.pipes++; pipeMeshes.push(pipeBetween(k === 0 ? POS.pumpOut : PIPE_ROUTE[k - 1], PIPE_ROUTE[k])); }
    if (gs.pumpHere && !day.boom && !markers.some((m) => m.id === 'pile')) buildPipeMarkers();
    // trouble, and the markers that go with it
    gs.blocked = s.bl;
    const blockM = markers.find((m) => m.id === 'block');
    if (s.bl >= 0 && !blockM) blockMarker(); else if (s.bl < 0 && blockM) removeMarker(blockM);
    const blowM = markers.find((m) => m.id === 'blowout');
    if (s.bo) { if (!gs.blowout) gs.blowout = { name: s.bo[0], p: P(s.bo[1], s.bo[2]), cells: new Set() }; if (!blowM) blowoutMarker(gs.blowout.p); }
    else { gs.blowout = null; if (blowM) removeMarker(blowM); }
    const rebM = markers.find((m) => m.id === 'rebarUp');
    if (s.rb) { liftBars.visible = true; liftBars.position.set(s.rb[0], liftBars.position.y || 0.2, s.rb[1]); if (!rebM && !gs.rebarFixed) { gs.rebarDue = s.rb[2]; rebarMarker(s.rb[0], s.rb[1], s.rb[2]); } }
    else { liftBars.visible = false; if (rebM) removeMarker(rebM); }
    gs.truck = s.tr ? { no: s.tr[0], left: s.tr[1], waiting: !!s.tr[2] } : null;
    gs.mixState = s.ms; gs.pouredM3 = s.pm; gs.waste = s.wa;
    gs.panPasses = s.pp; gs.bladePasses = s.bp;
    const flags = (str, arr, fn) => str.split('').forEach((c, k) => { if (c === '1' && arr[k] && !arr[k].done) { arr[k].done = true; if (fn) fn(arr[k]); } });
    flags(s.ti, site.ties, (t) => { t.bar.visible = false; t.twist.visible = true; });
    flags(s.cu, site.cuts, (t) => { t.bar.scale.y = 0.08; t.bar.position.y = rebarY() + 0.03; });
    flags(s.ed, site.edges);
    gs.edgesDone = s.en;
    // the trucks, the pump man, the van, the laser, the weather
    const [pv, mv, gv, pl, vw, tv, rain] = s.veh;
    pump.visible = !!pv; if (pv) pump.position.set(pv[0], 0, pv[1]);
    mixer.visible = !!mv; if (mv) { mixer.position.set(mv[0], 0, mv[1]); mixer.rotation.y = Math.PI; }
    pumpGuy.visible = !!gv; if (gv) { pumpGuy.position.set(gv[0], pumpGuy.position.y, gv[1]); pumpGuy.rotation.y = gv[2]; }
    pile.visible = !!pl;
    vanBack.want = vw;
    tripod.visible = !!tv;
    if (rain) rainUntil = performance.now() + 1500;
    // the people and animals the host's day sent over
    const seen = new Set();
    s.wk.forEach(([uid, kind, x, z, ry]) => {
      seen.add(uid);
      let w = net.remoteWalkers.get(uid);
      if (!w) {
        const m = kind === 'p' ? makePerson(chance(0.35) ? { vest: pick([0xd4f53c, 0xff7a1a]) } : {}) : makeDog(kind === 'c');
        m.traverse((o) => { if (o.isMesh) o.castShadow = true; });
        m.position.set(x, 0, z);
        scene.add(m);
        w = { m, kind, x, z, ry, phase: 0 };
        net.remoteWalkers.set(uid, w);
      }
      w.x = x; w.z = z; w.ry = ry;
    });
    net.remoteWalkers.forEach((w, uid) => { if (!seen.has(uid)) { scene.remove(w.m); net.remoteWalkers.delete(uid); } });
    if (s.hp) {
      // the host's helper: from the host's company, so in its colours only if the host plays in MixMaster
      if (!net.remoteHelper) { net.remoteHelper = makePerson({ vest: 0xd4f53c, hat: 'hard', hatColor: 0xf2f0ea, g: 'm', logo: !!net.hostMm }); scene.add(net.remoteHelper); }
      net.remoteHelper.userData.goal = s.hp;
    } else if (net.remoteHelper) { scene.remove(net.remoteHelper); net.remoteHelper = null; }
    ufo.g.visible = !!s.uf;
    if (s.uf) { ufo.g.position.set(s.uf[0], s.uf[1], s.uf[2]); ufo.beam.visible = !!s.uf[3]; }
    // the crew, the host among them
    const here = new Set();
    s.crew.forEach(([id, name, x, z, yaw, tool, act, ax, az, fl, pr, fa, fp]) => {
      if (id === net.me) return;
      here.add(id);
      const c = crewMember(id, name);
      crewUpdate(c, { x, z, yaw, tool, act, ax, az, fl, pr, fa, fp });
    });
    net.crew.forEach((c, id) => { if (!here.has(id)) { if (c.m) scene.remove(c.m); if (c.stream) scene.remove(c.stream); net.crew.delete(id); } });
  }
  /** Walkers, the helper and the saucer between snapshots: eased to where the host says they are. */
  function renderRemoteWorld(dt) {
    const k = 1 - Math.exp(-dt * 8);
    net.remoteWalkers.forEach((w) => {
      const p = w.m.position, u = w.m.userData, ox = p.x, oz = p.z;
      p.x = lerp(p.x, w.x, k); p.z = lerp(p.z, w.z, k);
      p.y = groundY(p.x, p.z);
      w.m.rotation.y = turnTo(w.m.rotation.y, w.ry, k);
      const moved = hyp(p.x, p.z, ox, oz);
      w.phase += moved * (w.kind === 'p' ? 4.5 : 9);
      if (u.legs) u.legs.forEach((l, n) => { l.rotation.z = moved > 0.003 ? Math.sin(w.phase + (n % 2) * Math.PI) * 0.6 : l.rotation.z * 0.9; });
      else if (u.legL) { u.legL.rotation.x = moved > 0.003 ? Math.sin(w.phase) * 0.5 : u.legL.rotation.x * 0.85; u.legR.rotation.x = -u.legL.rotation.x; }
    });
    const h = net.remoteHelper;
    if (h && h.userData.goal) {
      const [x, z, ry] = h.userData.goal;
      h.position.x = lerp(h.position.x, x, k); h.position.z = lerp(h.position.z, z, k);
      h.rotation.y = turnTo(h.rotation.y, ry, k);
    }
    if (ufo.g.visible) { ufo.g.rotation.y += dt * 1.5; ufo.lights.forEach((l, n) => l.material.color.setHex(LIGHT_COLS[(n + Math.floor(toolT * 6)) % LIGHT_COLS.length])); }
    if (mixer.visible && gs.truck) {
      mixGuy.visible = true;
      mixGuy.position.set(mixer.position.x - 3.2, 0, mixer.position.z + 1.9);
      mixGuy.rotation.y = -1.9;
    } else mixGuy.visible = false;
  }
  function guestEnd(m) {
    closeAllModals();
    gs.phase = 'end';
    $('#hud').hidden = true;
    $('#eRank').textContent = m.rank;
    $('#eScore').textContent = m.score;
    $('#ePay').innerHTML = m.pay;
    // the host's report, with how long you played and the state of your boots rather than the host's
    $('#eStats').innerHTML = String(m.stats).replace(/(<span>Played for<\/span><b>)[^<]*(<\/b>)/, `$1${playedFor()}$2`)
      .replace(/(<span>Boots<\/span><b>)[^<]*(<\/b>)/, `$1${bootsRow()}$2`);
    $('#eStory').innerHTML = m.story;
    $('#btnAgain').hidden = true;
    $('#end').hidden = false;
    if (m.verdict) setTimeout(() => say(m.verdict, 'manager'), 900);
  }

  /** Every frame, for a day played together. */
  function netTick(dt) {
    if (!inTeam()) return;
    const now = performance.now();
    if (gs.phase !== 'end' && gs.phase !== 'title') {
      if (isGuest() && now - net.meAt > 120) { net.meAt = now; netSend(Object.assign({ t: 'me' }, myState())); }
      if (now - net.diffAt > 200) { net.diffAt = now; sendDiffs(); }
      if (isHost() && now - net.snapAt > 200) { net.snapAt = now; hostPassWatch(); netSend(snapshot()); }
    }
    updateCrew(dt);
    if (isGuest()) renderRemoteWorld(dt);
  }

  // ------------------------------------------------------------------ title
  function showTitle(seed) {
    newCast();
    chatterAt = 0;
    net.seed = seed === undefined ? Math.floor(Math.random() * 2147483647) : seed;
    seeded(net.seed, () => {
      day = newDay();
      // a boom follows one hose on one phone; a day for a crew has the line pump
      if (net.role === 'host' || net.role === 'guest') day.boom = false;
      placeLayout();
      gs = freshState();
      buildSite();
      buildHall();
    });
    freshSurface();
    cellsDirty = true;
    // The night before is yours, not the crew's: rolled outside the day's seed, so one of a crew
    // can turn up fresh and another still smelling of it.
    lastNight = Math.random() < 0.22 ? fresh(L.hangoverCard) : '';
    $('#titleJoke').textContent = fresh(L.titleJokes);
    const season = { winter: 'Winter', spring: 'Spring', summer: 'Summer', autumn: 'Autumn' }[day.season];
    $('#dayCard').innerHTML = [
      [`${season}`, `${day.baseTemp.toFixed(0)} °C`],
      ['Humidity', `${day.rh}%`],
      ['Wind', `${day.wind} m/s`],
      ['Slab', `${day.area} m² · ${day.thick} mm`],
      ['Concrete', `${volumeNeeded().toFixed(1)} m³${day.area > 50 ? ' · ride-on' : ''}`],
      ['Pump', day.boom ? 'Boom pump' : 'Line pump'],
      ['Where', day.indoor ? 'Inside the new hall' : 'Out in the open'],
      ['Last night', lastNight ? 'Too much' : 'Early night'],
    ].map(([k, v]) => `<div>${k}<b>${v}</b></div>`).join('');
    const best = Number(store('pourday.best') || 0);
    $('#bestLine').textContent = best ? `Best shift so far: ${best} points` : 'Pump at seven. Mixer at half past. Probably.';
    $('#title').hidden = false;
    $('#end').hidden = true;
    $('#hud').hidden = true;
  }
  function resetWorld() {
    puddles.clear();
    spillBlobs.clear();
    if (gs && gs.cells) gs.cells.forEach((c) => { delete c.boards; });
    retch.t = 0;
    markers.slice().forEach(removeMarker);
    walkers.slice().forEach((w) => scene.remove(w.m));
    walkers.length = 0;
    drives.length = 0;
    pipeGroup.clear();
    pipeMeshes.length = 0;
    pump.visible = false; mixer.visible = false; pumpGuy.visible = false; pile.visible = false; tripod.visible = false;
    freshSurface();
    tarp.visible = false;
    vanBack.open = 0.001; vanBack.want = 0; vanBack.onOpen = null;
    flights.length = 0;
    kneel = 0;
    // yesterday's tools back in the van
    Object.values(lying).forEach((g) => { g.visible = false; });
    Object.values(machines).forEach((m) => { m.group.visible = false; m.spin = 0; });
    floatTool.visible = floatPole.visible = endHose.visible = stream.visible = false;
    hideHose(); hideStream();
    streamOn = false;
    hideStream();
    workTrowel.visible = workArm.visible = false;
    boomAim.x = NaN;
    mixGuy.visible = false;
    pv.forEach((p) => { p.life = 0; });
    pSize.fill(0);
    rainUntil = 0;
    cupT = 0;
    helpPour.on = false;
    liftBars.visible = false;
    if (helper.m) scene.remove(helper.m);
    helper.m = null; helper.state = 'off';
    flipT = 0; flipHand.visible = false;
    player.fall = 0;
  }
  window.addEventListener('pointerdown', audioStart, true);
  // ------------------------------------------------------------------ settings
  // Everything the player can set, on one screen, from the title or the pause menu: the switches
  // and volumes for sound, music and voices, and how the thumbs feel. Kept on the phone.
  let settingsOpen = false;
  function settingsShow() {
    const tog = (id, on) => { const b = $(id); b.textContent = on ? 'On' : 'Off'; b.classList.toggle('on', on); };
    tog('#setSound', soundOn); tog('#setMusic', musicOn); tog('#setVoices', voicesOn);
    tog('#setRead', readNotes); tog('#setSubs', subsOn); tog('#setInvert', invertY);
    $('#volSound').value = Math.round(soundVol * 100);
    $('#volMusic').value = Math.round(musicVol * 100);
    $('#sensWalk').value = Math.round(walkSens * 100);
    $('#sensLook').value = Math.round(lookSens * 100);
    $('#walkVal').textContent = walkSens.toFixed(1) + '×';
    $('#lookVal').textContent = lookSens.toFixed(1) + '×';
    const mine = myVoices(), at = mine.findIndex((v) => v.n === castFor('me', 'm', VOICES.me[0]));
    $('#myVoiceVal').textContent = at >= 0 ? `${at + 1} of ${mine.length} · ${accentOf(mine[at].l)}` : '';
    const book = voicesOnOffer(), men = book.filter((v) => v.g === 'm').length, women = book.filter((v) => v.g === 'f').length;
    $('#voiceInfo').textContent = book.length
      ? `This phone has ${book.length} English voice${book.length > 1 ? 's' : ''}: ${men} men's, ${women} women's${book.length - men - women ? `, ${book.length - men - women} it won't say` : ''}. Fewer than the cast, and some share one at another pitch.`
      : 'No English voices on this phone yet. Google\'s speech engine, from the Play Store, has a good few.';
  }
  /** The voices a working man could have on this phone: the men's, or all of them if it won't say. */
  function myVoices() {
    const book = voicesOnOffer(), men = book.filter((v) => v.g === 'm');
    return men.length ? men : book;
  }
  function accentOf(tag) {
    const c = String(tag || '').split(/[-_]/)[1] || '';
    return { GB: 'British', US: 'American', AU: 'Australian', IN: 'Indian', IE: 'Irish', ZA: 'South African', NG: 'Nigerian', NZ: 'New Zealand', CA: 'Canadian', SG: 'Singapore' }[c.toUpperCase()] || tag || 'English';
  }
  function nextMyVoice() {
    const mine = myVoices();
    if (!mine.length) { $('#myVoiceVal').textContent = 'no voices on this phone yet'; return; }
    const next = mine[(mine.findIndex((v) => v.n === cast.me) + 1) % mine.length];
    store('pourday.myVoice', next.n);
    // whoever had that voice today gets another one on their next line
    Object.keys(cast).forEach((k) => { if (cast[k] === next.n) { delete cast[k]; delete castShift[k]; delete castPitch[k]; } });
    settle('me', next.n, VOICES.me[0]);
    settingsShow();
    hush();
    say(fresh(L.myVoiceTry), 'me', 2);
  }
  function openSettings() {
    audioStart();
    settingsOpen = true;
    input.action = false;
    settingsShow();
    $('#settings').hidden = false;
  }
  function closeSettings() {
    settingsOpen = false;
    $('#settings').hidden = true;
    $('#howtoScreen').hidden = true;
  }
  $('#btnSettings').addEventListener('click', openSettings);
  $('#btnSetDone').addEventListener('click', closeSettings);
  $('#btnHowDone').addEventListener('click', closeSettings);
  $('#btnHowTo').addEventListener('click', () => howToPlay());
  $('#setSound').addEventListener('click', () => { setSound(!soundOn); settingsShow(); sfx('chime'); });
  $('#setMusic').addEventListener('click', () => { setMusic(!musicOn); settingsShow(); });
  $('#setVoices').addEventListener('click', () => { setVoices(!voicesOn); settingsShow(); if (voicesOn) say('"Voices on. God help us."', 'foreman'); });
  $('#setRead').addEventListener('click', () => { readNotes = !readNotes; store('pourday.readNotes', readNotes ? 'on' : 'off'); settingsShow(); if (readNotes) say('"That\'s me. That\'s what I sound like. Great."', 'me', 1); });
  $('#setMyVoice').addEventListener('click', nextMyVoice);
  $('#setSubs').addEventListener('click', () => { subsOn = !subsOn; store('pourday.subs', subsOn ? 'on' : 'off'); settingsShow(); });
  $('#setInvert').addEventListener('click', () => { invertY = !invertY; store('pourday.invertY', invertY ? 'on' : 'off'); settingsShow(); });
  $('#volSound').addEventListener('input', (e) => {
    soundVol = Number(e.target.value) / 100; store('pourday.volSound', String(soundVol));
    if (master && soundOn) master.gain.setTargetAtTime(0.9 * soundVol, ac.currentTime, 0.05);
  });
  $('#volSound').addEventListener('change', () => sfx('chime'));
  $('#volMusic').addEventListener('input', (e) => { musicVol = Number(e.target.value) / 100; store('pourday.volMusic', String(musicVol)); musicWant = -1; });
  $('#sensWalk').addEventListener('input', (e) => { walkSens = Number(e.target.value) / 100; store('pourday.sensWalk', String(walkSens)); settingsShow(); });
  $('#sensLook').addEventListener('input', (e) => { lookSens = Number(e.target.value) / 100; store('pourday.sensLook', String(lookSens)); settingsShow(); });
  $('#btnStart').addEventListener('click', () => {
    if (isGuest() || net.role === 'joining') return;
    if (isHost()) {
      // the whole crew clocks in together; nobody joins a day already under way
      if (!net.crew.size) { toast('Nobody has joined yet. Wait for them, or leave and play on your own.', 'warn'); return; }
      netSend({ t: 'start' });
    }
    beginDay();
  });
  $('#btnReroll').addEventListener('click', () => { showTitle(); sendLobby(); });
  // playing together
  $('#lobbyName').value = store('pourday.name') || '';
  $('#btnTogether').hidden = !canNet();
  $('#btnTogether').addEventListener('click', () => { $('#lobby').hidden = !$('#lobby').hidden; titleButtons(); });
  $('#btnHost').addEventListener('click', () => { audioStart(); hostDay(); });
  $('#btnJoin').addEventListener('click', () => { audioStart(); joinDay(); });
  $('#btnLeave').addEventListener('click', () => leaveCrew());

  // A new Pour Day, from the app that can fetch one (the Pour Day app on its own; MixMaster has its
  // own updater in Settings). The app says where it has got to; the button takes the next step.
  function showUpdate(u) {
    const v = u && u.v ? `<b>Pour Day ${u.v}</b>` : 'A new Pour Day';
    const say = {
      ready: [`${v} is out. New stuff on site, same concrete.`, 'Update'],
      downloading: [`Fetching ${v}… ${u && u.p || 0}%`, ''],
      permission: [`${v} is here. The phone has to let Pour Day install it — once, in the next screen.`, 'Allow'],
      install: [`${v} is here.`, 'Install'],
      failed: ['The update didn\'t come through. No signal on site?', 'Try again'],
    }[u && u.s];
    $('#updBar').hidden = !say;
    if (!say) return;
    $('#updText').innerHTML = say[0];
    $('#btnUpd').textContent = say[1];
    $('#btnUpd').hidden = !say[1];
  }
  window.pdUpdate = (json) => { try { showUpdate(JSON.parse(json)); } catch (e) { /* nothing to show */ } };
  $('#btnUpd').addEventListener('click', () => { try { appBridge.updateNext(); } catch (e) { /* no updater */ } });
  try { if (appBridge && appBridge.updateState) showUpdate(JSON.parse(appBridge.updateState())); } catch (e) { /* no updater */ }

  // Inside an app the page has a way out; in a plain browser there isn't one.
  const bridge = appBridge;
  const quit = () => { if (bridge) bridge.quit(); };
  $('#btnQuitTitle').hidden = !bridge;
  $('#btnQuitEnd').hidden = !bridge;
  $('#btnQuitTitle').textContent = QUIT_LABEL;
  $('#btnQuitEnd').textContent = QUIT_LABEL;
  $('#btnQuitTitle').addEventListener('click', quit);
  $('#btnQuitEnd').addEventListener('click', quit);

  const PHASE_NAMES = { morning: 'the morning', prep: 'prep', pipes: 'the pipes', pour: 'the pour', wash: 'washing up', cure: 'curing' };
  // ------------------------------------------------------------------ how to play
  // The whole game on one page, a section at a time. It is kept up to date with the game: whatever
  // changes how a day plays changes this page too, in the same commit (CLAUDE.md says so).
  const HOWTO = [
    ['The day', 'Get to the site, get it ready, lay the line, pour the slab, wash up, wait for it to harden, trowel it, pack up and go home. It\'s one day, from the alarm to the pay slip, and the clock only runs while you play.'],
    ['Your thumbs', '<b>Left thumb</b> walks; push it all the way to run (not in wet concrete, not carrying anything, not with your energy gone, and not when you badly need a shit). <b>Right thumb</b> looks around.\n<b>Hold the big button</b> to work: on whatever job glows nearby (the ring fills as you hold), or on the slab with what\'s in your hands. Every kind of job has its own colour, on its ring, its name and the map: yellow the hammer\'s, blue the mesh, pink the laser, orange the pipe line, purple corners and pipe collars, cyan the water tank, green your own business (toilet, phone, spare clothes, lunch), white the van — and red means now, before it gets worse. The names come down to chest height as you walk up to them. Slide your thumb on it while you hold and you look round as you work — that\'s how you steer the float and the trowels.\nThe button above <b>Wait</b> picks up, puts down and swaps tools. The one next to it switches the <b>laser</b> view, or fits <b>blades</b> and <b>pans</b> to a trowel machine. <b>Coffee</b> gives you energy (three cups; the kebab stand refills the thermos). The <b>finger</b> is for when words fail. <b>II</b> or the back gesture pauses.'],
    ['Getting it ready', 'Open the van: the tools wait on its ramp, the trowels at the bottom of it, the laser just inside the door. Walk up, look at one and press Pick up.\nThe jobs glow: check the formwork with the hammer (yellow), tie loose mesh with the pliers and wire, cut the bar sticking up with the rebar cutter (both blue). The laser (pink): set up the tripod, level the head (hold, and slide your thumb until the bubble sits in the ring), take a height off the benchmark peg, and check the boards at the corners — knock any that are out.'],
    ['The pump and the line', 'The pump arrives (Wait brings it sooner). Carry the pipes from the pile to the numbered markers, one at a time; the rubber end hose goes on the last one. On a boom pump day there are no pipes: the boom swings over the slab and the pump driver follows your hose with his remote. Mostly.'],
    ['Pouring', 'Pick up the hose at the end of the line. Look at a square and hold the big button: the concrete falls out of the hose\'s mouth onto the spot you\'re looking at, up to about five metres away. Keep it moving — held on one spot it builds a heap, and against the boards it goes over the top into the gravel.\nJust past the boards still counts as the slab; clearly out in the gravel is where it goes, onto the waste line, and the manager rings. Fresh concrete is dark and wet; the task card shows how many cubic metres are in and how many the slab needs.\nThe <b>laser</b> button shows the heights on the slab: green on height, red high, blue low; the receiver beeps fast high, slow low, steady on height. The <b>float</b> (from the van) levels it while it\'s wet; the <b>shovel</b> moves a heap to where it\'s low, and digs spilled concrete back out of the gravel. Walk in it and it\'s on your boots. When it\'s full, finish the pour.'],
    ['The trucks', 'Every truck is its own mix, and starts setting when it lands, at its own pace: the end the first truck filled is ready before the last. Now and then one comes wrong, or empty. Run short and you can order one more — it takes an hour and a half. A truck kept waiting costs money.\nOn a long pour the first end goes off while the last trucks are still coming. The <b>Hardness</b> panel shows up with the first truck: each truck\'s concrete (T1, T2…), and the orange tick on the bar is the hardest part. The map goes paler as it goes off, the square you look at says how hard it is and whose truck it was, and an empty hand does the thumb test. On a pour of three trucks or more, a truck\'s concrete that\'s all in and lies level (within about 8 mm) gets on with going off: it skips the slow start, and shows an arrow on the panel (T1 12%↑). So level what\'s in, and that end is ready while the last trucks are still coming. From 15% that end takes the edges and corners, from 25% the pans — put the hose down and trowel it between trucks, but fresh concrete poured over work already done means doing it again.'],
    ['Finishing', 'Footprints and marks come out with the float while it\'s under 50% hard, with the hand trowel under 70%, with the machines up to about 80% — after that they\'re in it for good.\nThe pan pass goes on from 25% (earlier and the pans dig in), the blade pass from 55%: fit blades with the button next to Put down. With a machine in hand, orange squares are the ones this pass hasn\'t been over, and blue ones are still too soft for it. A pass is done when nine squares in ten have had it, and it\'s judged on how hard each square was when the machine went over it.\n<b>The edges, all the way round</b>: every metre of board needs edging once it\'s 15% hard. Run the small <b>edge trowel</b> along the boards, or kneel with the <b>hand trowel</b> — look at the concrete right against a board and hold, then move along. With either in hand the metres still to do show orange along the boards (blue: too soft yet), and on the map; done ones get a smooth band. Past 85% an edge still closes, but it isn\'t pretty. The corners and pipe collars are hand-trowel jobs at the purple rings. The thumb test, and the square you look at, tell you how hard that bit is.'],
    ['Washing up', 'After the pour, carry every tool to the water tank and hold Wash before the concrete sets on it — two and a half hours and it\'s part of the tool, and chipping it off costs. Your boots too: empty hands at the tank. Every tool goes back to the van, washed, before you go home. The manager checks.'],
    ['Your body', 'Energy goes down all day. Coffee helps; so does the kebab stand, which has its own way of getting back at you.\nYou\'ll need a piss every few hours (sooner with coffee), and a shit after the kebab; the weather panel says when. The <b>toilet</b> is the blue box. The little window over the knob is red when somebody\'s in: pull the knob and they\'ll tell you about it, and they come out when they\'re done. Desperate for a piss? Behind the van — there\'s a ring for it, and sometimes a witness.\nHold on too long and you\'ll know: hopping from foot to foot, cramps that fold you in half. Then it happens. The <b>spare clothes</b> are behind the driver\'s seat in the van — and if it was a shit, hose yourself down at the water tank first. There\'s one pair of spare trousers and one pair of spare boots a day; after that it\'s a bin bag.'],
    ['Your phone', 'Texts and calls come up on the phone in your hand. It can slip out of your pocket, or go flying when you fall: it lands face down (orange case, on the ground, or in the pour) and rings to itself until you go back, look at it and hold the button. Face down on a building site means a cracked screen, often.'],
    ['People, dogs and cats', 'Passers-by, dogs and cats head for your slab: look at them and tap the big button to shout. The finger works on anything you look at, and some of them answer back. The pump and truck drivers are on the clock: stand about doing nothing for eight or ten seconds and they let you know. On a big slab the manager sends a helper. The manager rings anyway. Your family texts.'],
    ['Waiting', '<b>Wait</b> makes time fly: for the pump, for the next truck, standing guard over the slab (you\'re up if something happens), or napping in the van behind the wheel — fastest, and nobody guards the slab. Tap Wait again to get out.'],
    ['Going home', 'Home at 95% hard, with a pan pass and a blade pass done, every metre of edge, every corner and collar trowelled, the laser packed and every tool back in the van, washed. Then the report: flatness, marks, waste, the people you told where to go, how long you played, and the pay slip. Every mistake is on it.'],
    ['Playing together', '"Play together" on the title, with the phones close by and Bluetooth on (location too, on older phones). One hosts, the others join; the host\'s phone keeps the clock and the trucks. It\'s one slab and one set of tools — whoever holds a tool has it.'],
    ['Upright or sideways', 'Pour Day plays both ways. Turn the phone sideways for more slab and less thumb; upright gives your thumbs more room.'],
    ['Privacy', 'Pour Day collects nothing about you: no account, no ads, no tracking. Playing together, the phones talk to each other directly, over Bluetooth and Wi-Fi, and only the day being played passes between them. The voices are your phone\'s own.\nThe Pour Day app installed from a file, rather than from Google Play, looks on GitHub now and then for a newer version of itself.'],
    ['Credits', 'The 3D is drawn with three.js (MIT licence), and the letters are Manrope (SIL Open Font Licence 1.1). Both licences travel with the game.'],
  ];
  function howToPlay() {
    $('#htBody').innerHTML = HOWTO.map(([h, p]) => `<h3>${h}</h3>${p.split('\n').map((x) => `<p>${x}</p>`).join('')}`).join('');
    settingsOpen = true;
    input.action = false;
    $('#howtoScreen').hidden = false;
    $('#howtoScreen').scrollTop = 0;
  }
  function pauseMenu() {
    if (modalOpen || gs.phase === 'title' || gs.phase === 'end') return;
    hush();
    const choices = [
      { label: 'Resume', primary: true },
      { label: 'How to play', fn: () => howToPlay() },
      isGuest() ? { label: `Leave ${net.hostName}'s day`, danger: true, fn: () => leaveCrew() } : { label: isHost() ? 'Start a new day, for everyone' : 'Start a new day', fn: () => modal({
        who: 'New day', title: 'Walk off this one?', text: 'This slab stays how it is. The foreman will hear about it.', personal: true,
        choices: [{ label: 'New day', danger: true, fn: () => { resetWorld(); showTitle(); sendLobby(); } }, { label: 'Keep going', primary: true }],
      }) },
    ];
    if (isHost()) choices.push({ label: 'End the crew, play alone', fn: () => leaveCrew() });
    choices.splice(2, 0, { label: 'Settings: sound, voices, controls', fn: () => openSettings() });
    if (bridge) choices.push({ label: QUIT_LABEL, danger: true, fn: quit });
    modal({
      personal: true, pause: true,
      // The host keeps the clock, so the host's break stops it for everyone; a co-worker's doesn't.
      who: 'Paused', title: isGuest() ? 'Take five. (Nobody else does.)' : isHost() && net.started ? 'Take five. Everyone does.' : 'Take five.',
      text: `${clock(gs.t)}, ${PHASE_NAMES[gs.phase] || 'on site'}.` + (gs.poured ? ` Hardness ${Math.floor(gs.H)}%.` : '')
        + (isGuest() ? ` The clock is ${net.hostName}'s and it doesn't stop for you. The crew is still working.`
          : isHost() && net.started ? ' The clock stops for the whole crew until you\'re back. They can see that.'
          : ' Time stops while you\'re here. The concrete will pretend it did too.')
        + '\n\n↻ It plays upright or sideways: turn the phone sideways for more slab and less thumb.'
        // something threw and the game carried on: what it was, for whoever fixes it
        + (hiccups ? `\n\nThe game hiccuped ${hiccups === 1 ? 'once' : `${hiccups} times`} and carried on. For the developer: ${lastHiccup}` : ''),
      choices,
    });
  }
  $('#btnMenu').addEventListener('click', () => pauseMenu());
  /** The phone's back: pauses a shift under way, leaves from the title and end screens. */
  window.pdBack = () => {
    if (settingsOpen) { closeSettings(); return 'paused'; }
    if (gs.phase === 'title' || gs.phase === 'end') return 'quit';
    if (!modalOpen) pauseMenu();
    return 'paused';
  };
  $('#btnAgain').addEventListener('click', () => { resetWorld(); showTitle(); sendLobby(); });

  if (document.fonts && document.fonts.load) {
    Promise.all([document.fonts.load('800 20px Manrope'), document.fonts.load('700 20px Manrope')]).catch(() => {}).then(() => {});
  }
  buildDirt();
  showTitle();
  requestAnimationFrame(frame);

  if (DEBUG) {
    window.__pd = {
      get gs() { return gs; }, get day() { return day; },
      get near() { return nearMarker && nearMarker.id; }, get target() { return target && target.idx; }, get input() { return input; },
      get fps() { return fpsNow; }, context: () => context(),
      markers, player, simulate, finishPour, tryGoHome, completePass, nuisance, fall,
      edgeMetres, edgeWorkLeft, edgeWorkText, firstLoad, slabFinished, SLAB, edgeAll: () => { for (const c of gs.cells) for (const [di, dj] of boardsOf(c)) edgeDone(c, sideBit(di, dj)); }, get edgeSpot() { return edgeSpot && { i: edgeSpot.c.i, j: edgeSpot.c.j, bit: edgeSpot.bit }; },
      get hardHtml() { return $('#hard').hidden ? null : $('#hardPct').textContent + ' | ' + $('#hardEta').textContent; },
      doMarker(id) {
        const m = markers.find((x) => x.id === id && x.active());
        if (!m) return false;
        const ok = m.done();
        if (ok !== false) netMarker(id);
        // the van opens at once in a test, rather than over a second of frames
        if (id === 'unload') { vanBack.open = 1; if (vanBack.onOpen) { const f = vanBack.onOpen; vanBack.onOpen = null; f(); } updateVanBack(0); }
        return true;
      },
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
      walkers, mixer, pump, swapFit: () => swapFit(), discs: () => discs(), machines,
      spawnCross() { const side = 'w'; return spawnWalker('person', wander([farPoint(side), edgePoint(side)], 0.8), 1.4, { who: L.cross[0], from: side, onArrive: askToCross }); },
      spawnDog() { const side = 'w'; return spawnWalker('dog', [farPoint(side), edgePoint(side)], 3.2, { from: side, state: 'approach', onArrive: (w) => { w.state = 'eyeing'; w.pause = 30; } }); },
      reroll() { showTitle(); return day.area; },
      setDay(o) { Object.assign(day, o); }, get boomTip() { return boomTip.toArray().map((v) => +v.toFixed(2)); },
      rms: () => rms(), stamp: (k, x, z) => stamp(k, x, z, 0), marks: () => gs.cells.reduce((n, c) => n + c.marks.length, 0),
      viewTools, walkerInSight: (all) => { const w = walkerInSight(all); return w && (w.kind + (w.cat ? ':cat' : '') + (w.who ? ':' + w.who : '')); }, thingInSight: () => thingInSight(), sayTest: (t, w) => { duckUntil = 0; talking = false; sayQ.length = 0; return say(t, w); }, sayQueued: (t, w, p) => say(t, w, p), get sayQ() { return sayQ; }, voiceDone: () => voiceDone(), get saidNow() { return saidLog.slice(-5); }, spillInReach: () => spillInReach(), unspill: (dt) => { const b = spillBlobs.children[0]; if (b) unspillTick(b, dt); return b ? [b.userData.v, b.userData.back || 0] : null; }, get dirt() { return gs.dirt; }, bootsGet: (a) => bootsGet(a), get boots() { return gs.boots || 0; }, trackPrints, myBoots, get vanFloor() { return !!gs.vanFloor; }, playedFor: () => playedFor(), endDay: () => endDay(), showDirt: () => showDirt(), phoneText: (f, t, v) => phoneText(f, t, v), phoneCall: (f, t, v) => phoneCall(f, t, v), get phoneOn() { return phoneOn; }, dropPhone: () => dropPhone(), hosePts: () => hoseCurve.points.map((v) => [+v.x.toFixed(2), +v.y.toFixed(2), +v.z.toFixed(2)]), audioState: () => [ac && ac.state, +strokeAt.toFixed(1), loops._pumpEng ? +(loops._pumpEng.frequency.value * 120).toFixed(0) : 0], pickUpPhone: () => pickUpPhone(), get phoneDown() { return gs.phoneDown; }, accident: (k) => accident(k), updateLoo: (dt) => updateLoo(dt), get loo() { return gs.loo; }, get leaver() { return leaver; }, looSignMat, get cramp() { return cramp.t; }, changeClothes: () => changeClothes(), pissBehindVan: () => pissBehindVan(),
      speakerTest: () => { pumpGuy.visible = true; pumpGuy.position.set(player.x + 5, 0, player.z + 3); duckUntil = 0; say('"Oi! Over here! The hose, not the view!"', 'pump'); },
      toastTest: () => { toast('The formwork on the north side is 4 mm low.', 'warn'); toast('Laser on. It beeps. You beep back.', 'good'); }, idle, updateIdle: (dt) => updateIdle(dt), get inMixMaster() { return inMixMaster; }, get castShift() { return castShift; }, castFor: (k) => castFor(k, genderOf(k), (VOICES[k] || [1])[0]), newCast: () => newCast(), nextMyVoice: () => nextMyVoice(), personVoice: (g, k) => personVoice(g, k), kidVoice: (k, g) => kidVoice(k, g), net, pourAt: (x, z, dt) => { target = cellAt(x, z); if (target) { target._hx = x; target._hz = z; pourOut = null; } else pourOut = pastTheBoards(x, z); pourTick(dt); return target ? 'in' : pourOut ? (pourOut.inside ? 'board' : 'out') : 'nowhere'; }, flowTick: (dt) => flowTick(dt), get pourOut() { return pourOut; }, spillBlobs, pourCell: (k, dt) => { const c = gs.cells[k]; c._hx = gx(c.i) + 0.5; c._hz = gz(c.j) + 0.5; target = c; pourTick(dt); }, crewPoke: (to, kind) => netSend({ t: 'poke', to, kind }), shovelCell: (k, dt) => { const c = gs.cells[k]; c._hx = gx(c.i) + 0.5; c._hz = gz(c.j) + 0.5; shovelTick(c, dt); },
      packVan: () => { if (held()) putDown(true); TOOL_IDS.forEach((id) => { const t = gs.tools[id]; if (t && t.in !== 'gone' && id !== 'hose' && TOOL_HOME[id]) { const [x, z, yaw] = TOOL_HOME[id]; gs.tools[id] = { in: 'ground', x, z, yaw }; } }); gs.dirt = {}; },
      toolsOut: () => toolsOut(), soakTools: (dt) => soakTools(dt), jobInConcrete: (id) => jobInConcrete(markers.find((m) => m.id === id)), dirtyTools: () => dirtyTools(), addDirt: (id, a) => addDirt(id, a), helper, helpPour, flip: () => flip(), gesture: (who, kind) => gesture(who === 'pump' ? pumpGuy : who === 'mixer' ? mixGuy : walkers[0] && walkers[0].m, kind, 3, who === 'pump'), pumpGuy, mixGuy, useLoo: () => useLoo(), needs: () => gs.needs, rebarUp: () => rebarUp(), startPumpHelp: () => startPumpHelp(), shovelTick: (dt) => shovelTick(target, dt), sendHelper: () => sendHelper(), breakMachine: (id) => breakMachine(id), leaveTheMess: (o, d) => leaveTheMess(o, d), van, vanPoint, layoutObs, POS, PIPE_ROUTE, ENTRY, hall, chatterNow: () => { chatterAt = 1; duckUntil = 0; updateChatter(); }, L, get cast() { return cast; },
      packUp: () => packUp(), get packing() { return gs.packing; }, tooLate: () => tooLate(),
      setupLaser: () => { gs.carrying = null; gs.laserInVan = false; gs.laserSetup = 4; gs.prep.laser = true; tripod.visible = true; site.levelChecks.forEach((c) => { c.done = true; c.fixed = true; }); },
      get lvl() { return lvl; }, get levelChecks() { return site.levelChecks; },
      retchNow: () => startRetch(), get retch() { return retch; }, hangoverNow: () => hangoverNow(), trip: () => trip(),
      mishapNow: () => { mishapAt = 1; duckUntil = 0; updateMishaps(); }, rantNow: () => { rantAt = 1; duckUntil = 0; updateManager(); },
      get spilled() { return gs.spilled || 0; }, openSettings: () => openSettings(),
      visit: (k, away) => ({ ufo: ufoVisit, ball: ballVisit, cat: catVisit, drone: droneVisit, bag: bagVisit })[k](!!away),
      odd, slabReport: () => slabReport(), glyphs: () => gs.cells.reduce((n, c) => n + c.marks.filter((m) => m.kind === 'glyph').length, 0),
      get said() { return saidLog; },
      get audio() { return ac && ac.state; }, get sound() { return soundOn; }, get scene() { return scene; }, get camera() { return camera; },
    };
  }
})();
