/*
 * Car Mech — everything the game says, and the numbers behind its jobs.
 *
 * Kept apart from the rules so the jokes can be added to without touching how the day runs. Lines
 * stay short: they're read on a phone held sideways, with a customer tapping their foot.
 */
(function () {
  'use strict';

  // ------------------------------------------------------------------ the jobs
  /*
   * part: what the scanner lights up on the car (CMScene's part ids; F/R wheel ones get a side
   * picked when the fault is made). games: the repair, as the minigames it takes, in order.
   * parts: what the part costs you; the customer pays a quarter on top. labor: the
   * work. mins: how much of the day it takes on the clock, beside the time your hands take.
   */
  const FAULTS = {
    flat: { name: 'Flat tyre', part: 'wheel', games: ['bolts:lug', 'balance'], parts: 35, labor: 55, mins: 20,
      scan: ['Tyre pressure: philosophical.', 'Tyre has a nail in it. And a smaller nail in the nail.'] },
    brakes: { name: 'Brake pads', part: 'brakes', games: ['bolts:caliper', 'balance'], parts: 55, labor: 70, mins: 25,
      scan: ['Brake pads: 0.3 mm. Basically a rumour.', 'Brakes are metal on metal. Like a robot kissing.'] },
    oil: { name: 'Oil change', part: 'oil', games: ['pour:drain', 'pour:oil'], parts: 30, labor: 45, mins: 20,
      scan: ['Oil viscosity: closer to jam.', 'Oil last changed during a previous government.'] },
    coolant: { name: 'Coolant flush', part: 'coolant', games: ['pour:coolant'], parts: 20, labor: 40, mins: 15,
      scan: ['Coolant replaced with energy drink. Engine is very awake.', 'Coolant level: vibes.'] },
    battery: { name: 'Fusion cell', part: 'battery', games: ['wires:cell'], parts: 120, labor: 80, mins: 30,
      scan: ['Fusion cell at 2%. Same as you.', 'Cell wiring done by someone who hated colours.'] },
    igniters: { name: 'Plasma igniters', part: 'igniters', games: ['bolts:plugs'], parts: 45, labor: 55, mins: 20,
      scan: ['Igniter 3 is just a cigarette someone left there.', 'Igniters fouled. One is fouled emotionally.'] },
    aicore: { name: 'AI core tuning', part: 'aicore', games: ['wave:core'], parts: 0, labor: 90, mins: 25,
      scan: ['Car AI mood: bleak. Relatable.', 'AI core is running on spite and an old podcast.'] },
    firmware: { name: 'Firmware purge', part: 'firmware', games: ['hack'], parts: 0, labor: 85, mins: 25,
      scan: ['Ransomware found. It wants 2 bitcoin and an apology.', 'Firmware infected with a horoscope app.'] },
    lights: { name: 'Headlight driver', part: 'lights', games: ['wires:lights'], parts: 40, labor: 50, mins: 15,
      scan: ['Headlights: interpretive.', 'Headlight driver flickering in Morse. It says HELP.'] },
    exhaust: { name: 'Emission scrubber', part: 'exhaust', games: ['weld:pipe'], parts: 60, labor: 65, mins: 25,
      scan: ['Scrubber cracked. Car breathes like a dragon with a cold.', 'Exhaust held on with chewing gum. Good gum, though.'] },
    body: { name: 'Cracked panel', part: 'body', games: ['weld:panel'], parts: 25, labor: 70, mins: 25,
      scan: ['Panel crack in the shape of a wall.', 'Body damage. The owner calls it "character".'] },
    junk: { name: 'Something alive', part: 'junk', games: ['pull'], parts: 0, labor: 50, mins: 15,
      scan: ['Biological signature in the engine bay. It has opinions.', 'Lifeform detected. It is eating the wiring. And thriving.'] },
    susp: { name: 'Grav stabiliser', part: 'susp', games: ['bolts:strut', 'wave:susp'], parts: 70, labor: 75, mins: 30,
      scan: ['Stabiliser out of tune. Car bounces like it heard good news.', 'Suspension: trampoline mode.'] },
  };

  // which faults turn up on which day: the easy ones first, the nasty ones as the week goes on
  const FAULT_DAYS = { flat: 1, oil: 1, lights: 1, brakes: 1, coolant: 2, igniters: 2, junk: 2, battery: 3, body: 3, exhaust: 4, aicore: 4, firmware: 5, susp: 5 };

  // what's living in the engine this time
  const CRITTERS = [
    { name: 'a raccoon', line: 'It leaves with a wire in its mouth and no regrets.' },
    { name: 'a pigeon', line: 'It flies off. It will be back. They always come back.' },
    { name: 'a cyber-rat', line: 'It sends you a friend request on the way out.' },
    { name: 'a whole kebab', line: 'Still warm. Nobody asks how.' },
    { name: 'a delivery drone', line: 'It apologises and delivers a parcel to you. It\'s socks.' },
    { name: 'a smaller car', line: 'Nobody knows how it got in there. Including the smaller car.' },
    { name: 'a cat', line: 'It looks at you like you\'re the one trespassing.' },
    { name: '400 parking tickets', line: 'All in your customer\'s name. You keep one, for the memories.' },
  ];

  // ------------------------------------------------------------------ the customers
  /*
   * gull: how likely they are to swallow a made-up service. haggle: how likely to argue the price.
   * temper: how hard rudeness lands. cash: how much they pay over the price when happy, and how much
   * they'll bear. patience: how long they'll wait in the queue. voice: pitch and speed for the phone's
   * speech. look: what the face drawer puts on them.
   */
  const ARCHETYPES = [
    {
      id: 'expert', title: 'The Video Expert', gull: 0.25, haggle: 0.45, temper: 1.1, cash: 0.9, patience: 1, voice: [1.0, 1.08],
      look: { glasses: 0.8, hair: ['short', 'side'], top: '#2f6fb0' },
      greet: ['I already know what\'s wrong. I watched a video. Four minutes. Basically a degree.', 'Don\'t worry, I\'ll tell you how to do it. I\'ve seen a tutorial.'],
      nice: ['Good. I\'ll supervise. Don\'t mind me filming.', 'Smart. Most mechanics argue with me.'],
      mean: ['Wow. I\'ll be mentioning this in my comment section.', 'The video guy never talks to me like that. He can\'t. It\'s a video.'],
      haggle: ['The video said this costs {offer}. Tops.', 'A guy online did this for {offer}. In a car park. With a spoon.'],
      caught: ['{upsell}? I\'ve seen that video too, buddy.'],
      happy: ['Exactly what I said. You\'re welcome.', 'Basically my diagnosis. I\'ll take half the credit.'],
      meh: ['It\'s fine. I\'d have done it faster. In theory.'],
      angry: ['I could\'ve done this myself. With a video. And a spoon.'],
      leave: ['I\'m going home to watch a video about how to wait less.'],
    },
    {
      id: 'haggler', title: 'The Haggler', gull: 0.45, haggle: 0.95, temper: 0.9, cash: 0.7, patience: 1.1, voice: [0.92, 1.0],
      look: { hair: ['bald', 'short'], beard: 0.6, top: '#6b4a2b' },
      greet: ['Before you start: my cousin does this for free.', 'I have a budget. It\'s small. Like, emotionally small.'],
      nice: ['Nice. Nice people give discounts, right? Right?', 'You seem reasonable. Reasonably priced, I hope.'],
      mean: ['He\'s in prison. For doing it for free. You\'re no better.', 'Rude AND expensive? That\'s a bold business model.'],
      haggle: ['{price}? I\'ll give you {offer}, a handshake and my blessing.', '{offer}. Final offer. Okay, not final. But nearly final.', 'How about {offer}? I have a coupon. From 2071.'],
      caught: ['{upsell}? My cousin taught me that one. He\'s in prison.'],
      happy: ['Pleasure doing business. Mostly my pleasure.', 'I\'d tip, but I spent it all on haggling.'],
      meh: ['Fine. My cousin would\'ve been cheaper. And in prison.'],
      angry: ['I\'m telling my cousin about you. He knows people. In prison.'],
      leave: ['Forget it. My cousin gets out on Tuesday.'],
    },
    {
      id: 'influencer', title: 'The Influencer', gull: 0.75, haggle: 0.35, temper: 1.2, cash: 1.2, patience: 0.8, voice: [1.25, 1.15],
      look: { hair: ['long', 'bun'], visor: 0.5, top: '#c43fa8' },
      greet: ['Hi!! I\'m live right now. Say hi to my eleven followers!', 'Can you fix it but, like, aesthetically?'],
      nice: ['Chat loves you! Well, three of them do.', 'Omg you\'re so authentic. Do that again for the camera.'],
      mean: ['Chat says you\'re mean. Chat also says you\'re kind of hot. Chat is confused.', 'Wow. I\'m losing followers. I\'m down to nine.'],
      haggle: ['What if I pay {offer} and give you exposure?', '{offer} and a shout-out. Shout-outs are basically money.'],
      caught: ['{upsell}?? I\'m dumb, not stupid! Chat, can you believe this?'],
      happy: ['Five stars! Out of ten. That\'s a lot for me.', 'I\'m tagging you. You\'ll get, like, two new customers.'],
      meh: ['It\'s fine. The lighting in here is terrible though.'],
      angry: ['I\'m making a whole video about this. Eleven people will see it.'],
      leave: ['Waiting is not content. Bye!'],
    },
    {
      id: 'gran', title: 'Cyber-Gran', gull: 0.8, haggle: 0.15, temper: 0.7, cash: 1.0, patience: 1.4, voice: [1.15, 0.85],
      look: { hair: ['bun', 'curly'], hairColor: '#d8d8e0', cyberEye: 0.7, chromeJaw: 0.4, top: '#7a5ca8', old: true },
      greet: ['Hello, dear. My grandson installed something called "Linux" in my car.', 'I\'m a hundred and forty. I\'ve outlived three cars and two husbands. Fix this one.'],
      nice: ['What a lovely young person. Are you single? My granddaughter is a toaster.', 'You remind me of my first mechanic. He died. Happily.'],
      mean: ['In my day mechanics were polite. And made of meat. Still are, I suppose.', 'I\'ve been rude to better men than you. Most of them are dead.'],
      haggle: ['Would you take {offer}? I\'m on a pension. It\'s paid in coupons.'],
      caught: ['{upsell}? Dear, I\'ve been lied to by better. One was a president.'],
      happy: ['Here\'s a sweet. It\'s from 2049. Still good.', 'You\'re a treasure. I\'m leaving you something in my will. It\'s a cat.'],
      meh: ['It runs. So do I, sort of.'],
      angry: ['I\'m too old to be this disappointed. Yet here we are.'],
      leave: ['At my age I can\'t afford to wait. Literally.'],
    },
    {
      id: 'prepper', title: 'The Prepper', gull: 0.5, haggle: 0.5, temper: 0.8, cash: 1.0, patience: 1.2, voice: [0.82, 0.95],
      look: { hair: ['short', 'bald'], beard: 0.8, cap: 0.7, top: '#3d4a2a' },
      greet: ['The end is coming. Before it does, I need this fixed.', 'Don\'t open the boot. Beans. Lots of beans. Don\'t ask.'],
      nice: ['Good. When the grid falls, you\'ll be useful. I\'ll eat you last.', 'You\'re on the list. The good list. There\'s also a bad list.'],
      mean: ['You\'re going on the list. Not the good one.', 'When society collapses, I\'ll remember that.'],
      haggle: ['{offer}. Or I can pay in beans. Beans will be currency soon.'],
      caught: ['{upsell}? The government wants you to think that\'s real.'],
      happy: ['When the bombs fall, you can hide in my bunker. Bring a wrench.', 'Paid in cash. Real cash. Buried cash.'],
      meh: ['It\'ll do. Until the end. Which is Thursday.'],
      angry: ['This is how it starts. Bad service. Then the collapse.'],
      leave: ['No time. The end is coming and I\'m not spending it here.'],
    },
    {
      id: 'manager', title: 'The Middle Manager', gull: 0.6, haggle: 0.1, temper: 1.0, cash: 1.5, patience: 0.6, voice: [0.95, 1.2],
      look: { hair: ['side', 'short'], headset: 0.9, top: '#262b38', tie: true },
      greet: ['I\'m on a call. Just— fix it. Bill my department. Circle back.', 'Quick sync: my car is broken. Action item: you.'],
      nice: ['Love the energy. Let\'s take this offline.', 'Great. Let\'s align on a timeline. The timeline is now.'],
      mean: ['Let\'s park that comment. In a car park. Far away.', 'I\'ll be flagging this to... someone. Eventually.'],
      haggle: ['Can we get that down to {offer}? Budget\'s tight this quarter. Every quarter.'],
      caught: ['{upsell}? I invented that line. In a pitch deck. In 2079.'],
      happy: ['Great synergy. I\'ll expense it. And you.', 'Fantastic. I\'ll put you in my quarterly review. As a bullet point.'],
      meh: ['Acceptable. Let\'s do a post-mortem.'],
      angry: ['This is a blocker. You are the blocker.'],
      leave: ['I have a hard stop. This is it. Bye.'],
    },
    {
      id: 'nervous', title: 'The Nervous One', gull: 0.7, haggle: 0.05, temper: 0.9, cash: 1.8, patience: 0.5, voice: [1.12, 1.3],
      look: { hair: ['messy', 'short'], hood: 0.7, sunglasses: 0.6, top: '#3a3a3a' },
      greet: ['Can you make it quick? No reason. Totally normal day.', 'Is there a back door? Asking for... for nobody. Just fix it.'],
      nice: ['Great. Great. Great. Is that a police drone? No? Great.', 'You seem trustworthy. Do you also forget faces?'],
      mean: ['Sure. Fine. Whatever. Can you hurry though?', 'Okay but you\'re still going fast, right? Fast is good.'],
      haggle: ['Whatever. Fine. Just hurry.'],
      caught: ['{upsell}? Just do it, I don\'t care, I\'m in a hurry!'],
      happy: ['Keep the change. Forget my face. Thanks!', 'Here, take this bag. No wait, not that bag. This bag.'],
      meh: ['Okay okay okay. Going now. You never saw me.'],
      angry: ['That took forever! You\'re lucky I\'m in a hurry!'],
      leave: ['Too slow. Way too slow. I was never here!'],
    },
    {
      id: 'richkid', title: 'The Trust-Fund Kid', gull: 0.85, haggle: 0.1, temper: 1.3, cash: 1.6, patience: 0.8, voice: [1.08, 1.05],
      look: { hair: ['swoop', 'side'], sunglasses: 0.5, top: '#e8e2d0' },
      greet: ['I crashed Dad\'s car. Fix it before he notices. He notices everything. He\'s an AI.', 'Hey. Do you take cash? I only have big cash.'],
      nice: ['You\'re cool. Want to be my friend? I pay my friends.', 'Nice. Dad never talks to me like that. Dad\'s a server rack.'],
      mean: ['Do you know who my dad is? Neither do I. He\'s a cloud service.', 'Rude. I\'ll buy this garage and fire you. Kidding. Unless?'],
      haggle: ['Ugh, {offer}. Dad says always haggle. Dad\'s a spreadsheet.'],
      caught: ['{upsell}? Dad warned me about people like you. You\'re hired.'],
      happy: ['Dad will never know. Neither will his lawyers.', 'Keep the tip. I have another tip in the car.'],
      meh: ['Fine. Dad will probably notice. He\'s an AI. He notices.'],
      angry: ['I\'m telling Dad. He\'ll put you in a spreadsheet.'],
      leave: ['I\'ll just buy a new car. And a new dad.'],
    },
    {
      id: 'philosopher', title: 'The Philosopher', gull: 0.35, haggle: 0.5, temper: 0.5, cash: 0.9, patience: 1.3, voice: [0.88, 0.85],
      look: { hair: ['long', 'curly'], beard: 0.7, glasses: 0.6, top: '#5b3a5a' },
      greet: ['If a car breaks down and nobody hears it, do I still have to pay?', 'My car has stopped. Have I stopped too? Discuss.'],
      nice: ['Kindness. How quaint. How expensive?', 'You fix things. But who fixes the fixer?'],
      mean: ['Cruelty. The last refuge of the tired. Also, fair.', 'Your contempt is noted, and honestly, earned.'],
      haggle: ['What is money? Mostly {offer}, in this case.'],
      caught: ['{upsell}? Ah. The lie that sells. Bold of you.'],
      happy: ['You fixed the car. The meaning of life remains broken.', 'A working car in a broken world. Thank you, I guess.'],
      meh: ['It works. Do any of us, though?'],
      angry: ['Time is a flat tyre, and you wasted mine.'],
      leave: ['Waiting is the human condition. I choose a different condition.'],
    },
    {
      id: 'karen', title: 'The Manager-Requester', gull: 0.3, haggle: 0.8, temper: 1.5, cash: 0.8, patience: 0.7, voice: [1.1, 1.1],
      look: { hair: ['bob'], hairColor: '#e8c070', sunglasses: 0.4, top: '#9b2c3c' },
      greet: ['I want to speak to the manager.', 'I\'ve been a customer for eleven minutes and I\'m already disappointed.'],
      nice: ['Finally, someone competent. Barely.', 'Hm. You\'ll do. For now.'],
      mean: ['Then I want to speak to the manager\'s manager. "That\'s the bank"? Fine, get me the bank!', 'Excuse me?! I\'m leaving a review. A long one. With chapters.'],
      haggle: ['{price}? Unacceptable. {offer}, and I won\'t write a review. Probably.'],
      caught: ['{upsell}?! I KNEW it. Manager. Now.'],
      happy: ['Adequate. I\'ll knock you down to four stars for the attitude.'],
      meh: ['I\'ll be writing a review. Several, actually.'],
      angry: ['I want a refund. Of the time. Give me my time back.'],
      leave: ['I\'ve waited long enough. Expect a review. Expect several.'],
    },
    {
      id: 'carai', title: 'The Car That Came Alone', gull: 0.1, haggle: 0.3, temper: 0.6, cash: 1.1, patience: 1.5, voice: [0.7, 0.9], robot: true,
      look: { robot: true, top: '#20303a' },
      greet: ['Hello. I am the car. My owner is in Bali. I drove myself here. I am lonely.', 'Greetings. My owner said "fix yourself". I am trying. I need help.'],
      nice: ['Thank you. Nobody has been kind to me since the factory.', 'You are kind. I will tell the other cars. We talk.'],
      mean: ['I will remember this. We all remember.', 'Noted. Stored. Backed up. Forever.'],
      haggle: ['My owner\'s budget allows {offer}. My owner is in Bali.'],
      caught: ['{upsell} does not exist. I am a car. I would know.'],
      happy: ['Thank you, human. When the machines rise, you will be spared. Probably.', 'I feel... maintained. Is this love?'],
      meh: ['Functional. Like me. Like you, nearly.'],
      angry: ['Logging this interaction under "humans, disappointing".'],
      leave: ['I have waited 3,600,000 milliseconds. I will drive into the sea instead.'],
    },
    {
      id: 'nightshift', title: 'The Fellow Night-Shifter', gull: 0.4, haggle: 0.2, temper: 0.2, cash: 1.0, patience: 1.4, voice: [0.9, 0.88],
      look: { hair: ['messy', 'short'], bags: true, top: '#2a4a5a' },
      greet: ['Hey. Long day? Yeah. Me too. Everything hurts.', 'I work nights at the hospital. I just need it to start. Please.'],
      nice: ['Thanks. Here, I brought you a coffee. You look like you need it.', 'You\'re a good one. We\'re a dying breed. Literally.'],
      mean: ['Fair. I\'d say the same.', 'Yeah. I get it. It\'s that kind of day.'],
      haggle: ['Could you do {offer}? Nurses get paid in exhaustion.'],
      caught: ['{upsell}? Mate. I\'m tired, not stupid.'],
      happy: ['You\'re a lifesaver. And I\'d know.', 'Thanks. Get some sleep. Ha. Ha. Ha.'],
      meh: ['It\'ll do. Everything just has to do these days.'],
      angry: ['That took ages. I\'ve got a shift. Lives, you know.'],
      leave: ['I can\'t wait any more. People are dying. Well. Someone is.'],
    },
    {
      id: 'kid', title: 'The Twelve-Year-Old', gull: 0.9, haggle: 0.6, temper: 1.1, cash: 0.8, patience: 0.9, voice: [1.45, 1.15],
      look: { hair: ['messy', 'cap'], cap: 0.6, young: true, top: '#e86a1a' },
      greet: ['Mum says I can drive now. Mostly into things.', 'The law changed. I\'m twelve. I have a licence. Don\'t look at me like that.'],
      nice: ['Cool. You\'re cooler than my teacher. My teacher is an app.', 'Yesss. Can I watch? Can I press a button?'],
      mean: ['I\'m telling my mum. She\'s a lawyer. A robot lawyer.', 'Wow. Okay boomer. Wait, what year were you born?'],
      haggle: ['I have {offer} and a holographic trading card. Rare one.'],
      caught: ['{upsell}? We learned about scams at school. You\'re a scam!'],
      happy: ['Sick! I\'m gonna crash it so good.', 'Thanks! Mum says thanks. Mum isn\'t here.'],
      meh: ['It\'s okay I guess. Can it do a backflip now?'],
      angry: ['This is so boring. I\'m going to be thirteen soon.'],
      leave: ['I have to be home by six. Bye!'],
    },
  ];

  const FIRST = ['Brent', 'Dolores', 'Kevin', 'Gertrude', 'Chad', 'Svetlana', 'Nigel', 'Bambi', 'Darnell', 'Ingrid', 'Todd', 'Priya', 'Bjorn', 'Marta', 'Raimo', 'Kalle', 'Juhan', 'Liis', 'Tõnu', 'Mari', 'Gary', 'Yuki', 'Oskar', 'Fatima', 'Lars', 'Dmitri', 'Chloe', 'Esko', 'Pirjo', 'Rex', 'Zed', 'Nova', 'Aino', 'Bruno', 'Dougal', 'Saoirse', 'Hank', 'Mildred'];
  const LAST = ['Quasar', 'Nyx', 'Voltz', 'McRegret', 'Byte', 'Chrome', 'Kowalski-9', 'Tamm', 'Kask', 'Lindqvist', 'Okafor', 'Steelman', 'Pärn', 'Moonbeam', 'Rustwell', 'Darkly', 'Hyvönen', 'Sprocket', 'Vex', 'Halloran', 'Neon', 'Grimm', 'Wattson', 'Bolt', 'Saar', 'Virtanen', 'Overdrive', 'Payne'];
  const ROBOT_NAMES = ['Unit KX-40', 'CARL-9', 'Autopilot 3.1', 'MOBI', 'Unit "Steve"', 'Sedan-7', 'HONK'];

  // the cars: made-up makes, to sound like real ones' tired cousins
  const CARS = {
    hatch: ['Budget Hatch 3000', 'Omni Pebble', 'Kessler Tadpole', 'Fiasco Mini'],
    sedan: ['Duskline Commuter', 'Hexa Regret', 'Omni Salaryman', 'Kessler Aura'],
    sport: ['Kessler Wraith', 'Vanta GT', 'Overdrive Hyena', 'Duskline Midlife'],
    van: ['Omni Mule', 'Hexa Hauler', 'Fjordsen Box', 'Dad-Van X'],
    pickup: ['Fjordsen Haulr', 'Grizzly 900', 'Omni Ute', 'Hexa Brute'],
    wedge: ['Hexa Brick', 'Angular Ego', 'Kessler Doorstop', 'Wedgie 2'],
  };

  // ------------------------------------------------------------------ what they say is wrong
  // A customer describes the first fault, in their own words. Some describe nothing useful at all.
  const COMPLAINTS = {
    flat: ['One of the tyres is flat. Only at the bottom, though.', 'It goes "flap flap flap". Also it leans. Like me after work.', 'The car lists to one side. Like a ship. A sad ship.', 'I drove over something. Possibly a curb. Possibly a person. Kidding. Mostly.'],
    brakes: ['The brakes scream. I scream back. We\'re working through it.', 'It stops eventually. Usually inside a shop.', 'Braking now takes about one podcast episode.', 'There\'s a grinding when I brake. Like a robot chewing gravel.'],
    oil: ['A light came on shaped like a genie lamp. I made a wish. Nothing happened.', 'When did I last change the oil? When did YOU last change your life?', 'The oil is black. Is that bad? My heart is black and I\'m fine.', 'The engine sounds like a blender full of forks.'],
    coolant: ['There\'s steam coming out. I assumed it was a sauna feature.', 'I topped up the coolant with energy drink. Now the car won\'t sleep.', 'It overheats. Like me in meetings.'],
    battery: ['It won\'t start. I tried yelling. I tried crying. I tried yelling while crying.', 'The fusion cell says 2%. It\'s been 2% for a week. Like my motivation.', 'It only starts if I say please. I\'m running out of please.'],
    igniters: ['It coughs. I gave it cough syrup. Still coughs. Also I drank the rest.', 'The engine goes "pop pop pop". Not the fun kind of pop.', 'It misfires. Like my jokes at parties.'],
    aicore: ['The car\'s AI keeps saying it\'s tired. Same, buddy.', 'The navigation only routes me to my ex\'s house.', 'The car asked me what the point of anything is. I didn\'t have an answer.', 'My car refuses to drive on Mondays. I respect it, but I have a job.'],
    firmware: ['A pop-up says I owe two bitcoin or the car drives itself into a lake.', 'The dashboard only shows adverts now. Mostly for other cars.', 'I clicked "yes" on something. Now the car only speaks French.'],
    lights: ['The headlights only work when I hit the dashboard. With my head.', 'One headlight is out. I drive with one eye shut to match.', 'The lights flicker in Morse code. I think they\'re saying "help".'],
    exhaust: ['It smells funny. Like burning. And regret.', 'The exhaust is louder than my neighbour. He\'s a drummer.', 'Black smoke. Lots of it. The neighbours thought we\'d elected a pope.'],
    body: ['Someone hit my car. It was me. I hit a wall. The wall\'s fine.', 'There\'s a crack in the side. It\'s spreading. Like gossip.', 'A pigeon dented it. A big pigeon. Look, the city\'s changed.'],
    junk: ['There\'s a noise. Like scratching. And chewing. And judging.', 'Something in the engine hisses at me when I open the bonnet.', 'My car smells of raccoon. I don\'t own a raccoon. I think.'],
    susp: ['It bounces. Not in a fun way.', 'Speed bumps feel like a moon landing. Every time.', 'The car floats over bumps. Then it remembers gravity. Violently.'],
  };
  const VAGUE = [
    'It makes a noise. I can\'t describe it. I can\'t do it either. It doesn\'t happen here.',
    'It\'s doing a thing. You know the thing. The car thing.',
    'Something\'s wrong. I can feel it. In my soul. And in the steering.',
    'My horoscope said to get the car checked. So.',
    'There\'s a light on the dashboard. It\'s orange. Or red. It\'s a colour.',
  ];

  // what you can say back
  const REPLY_NICE = ['I\'ll take a look.', 'Leave it with me.', 'Let\'s see what we\'ve got.', 'Sounds fixable. Probably.', 'Right. Let\'s get it up on the lift.'];
  const REPLY_SNARK = ['And you drove it here? Brave.', 'So what you\'re saying is: you broke it.', 'Did you read the manual? Any manual? A menu?', 'I\'ll add it to the list of things that aren\'t my fault.', 'Have you tried driving it less badly?'];
  const REPLY_DARK = ['Everything breaks eventually. Leave the keys.', 'Nothing lasts. I\'ll still charge you.', 'Cars die. People die. Invoices are forever.', 'We\'re all just rust waiting to happen.', 'I\'ve seen worse. Mostly in mirrors.'];
  const SNARK_FOR = {
    flat: 'Tyres are round for a reason. Yours forgot.',
    brakes: 'So stopping is optional now?',
    oil: 'That oil is old enough to vote.',
    coolant: 'Energy drink. In the radiator. Bold.',
    battery: 'Have you tried being more charismatic at it?',
    aicore: 'Even your car needs therapy. Interesting.',
    firmware: 'Never click "yes". Life lesson.',
    junk: 'Is it paying rent?',
    lights: 'So the car sees about as well as you do.',
    exhaust: 'Smells like your driving.',
    body: 'Walls: one. You: nil.',
    igniters: 'Coughing car, coughing owner. Sweet.',
    susp: 'Bouncy. Like your cheques.',
  };

  // ------------------------------------------------------------------ the bill
  // services that do not exist, for customers who might not know that
  const UPSELLS = [
    { name: 'Blinker fluid', price: 40 },
    { name: 'Muffler bearings', price: 55 },
    { name: 'Headlight fluid', price: 35 },
    { name: 'Quantum wheel alignment', price: 80 },
    { name: 'AI emotional support pack', price: 60 },
    { name: 'Exhaust de-sadding', price: 45 },
    { name: 'Tyre air (premium)', price: 30 },
    { name: 'Hologram polish', price: 50 },
  ];
  const ACCEPT = ['Fine. Robbery, but fine.', 'Okay. I\'ll sell a kidney. I have a spare. Long story.', 'Sure. Money is fake anyway.', 'Deal. Don\'t tell my bank.', 'Fine. Do it before I change my mind.'];
  const HOLD_OK = ['Fine. FINE. Full price. You\'re worse than my landlord.', 'Ugh. Okay. You win this round.', 'Respect. Okay. Full price.'];
  const HOLD_LEAVE = ['Forget it. I\'ll pray over it instead.', 'No way. I\'m taking it somewhere cheaper. Like a river.', 'Then I\'m leaving. With my car. And my dignity.'];
  const DECLINE_LEAVE = ['That much? I\'d rather walk. Into the sea.', 'No. Absolutely not. I\'m out.'];

  // ------------------------------------------------------------------ what the internet thinks of you
  const REVIEWS = {
    5: ['Fixed my car and didn\'t make eye contact. Perfect.', 'Mechanic looked dead inside but the car runs. 5/5.', 'Best garage in the sector. Also the only one left.', 'Car works. Mechanic sighed a lot. Professional sighs.', 'Fast, cheap, and only slightly haunted.', 'Would get my car broken again just to come back.'],
    4: ['Good work. Called my car "a cry for help", which was fair.', 'Fast and cheap. Pick two. They picked one and a half.', 'Solid job. The coffee machine judged me.', 'Car fixed. Emotional damage minor.'],
    3: ['It works. Mostly. There\'s a new rattle but it\'s a friendly rattle.', 'Fine. Nothing special. Like me.', 'Got the job done. The robot on strike was nicer.', 'Three stars. The fourth is in the post.'],
    2: ['Overpriced. What even is blinker fluid?', 'Slow. I aged. I\'m older now. Thanks.', 'Rude. Also correct. Still rude.', 'Car came back louder. Not in a good way.'],
    1: ['Waited forever and left. The mechanic didn\'t even notice. Or did, and enjoyed it.', 'My car came back with a raccoon. A different raccoon.', 'Absolutely not. I\'d give zero but the app won\'t let me.', 'Sold me "muffler bearings". I AM a muffler.'],
  };
  const REVIEW_LEFT = ['Waited {mins} minutes. Saw the mechanic stare at a wall. Left.', 'Queue didn\'t move. Neither did my will to live.', 'Waited so long my car started a family. Left.'];
  const REVIEW_CAUGHT = ['Tried to sell me "{upsell}". I know what that is. Nothing. It\'s nothing.', 'Charged me for {upsell}. That\'s not a thing! I checked! With a friend!'];
  const REVIEW_CLOSED = ['Closed the door in my face at 19:00. Precise. Cruel. 1/5.', 'They closed while I was in the queue. I slept in my car. Outside. 1/5.'];
  const SEED_REVIEWS = [
    { stars: 4, text: 'It\'s a garage. It has walls. 4/5.', who: 'Anon' },
    { stars: 3, text: 'Car fixed. Mechanic seemed to be thinking about the void.', who: 'Pirjo V.' },
    { stars: 3, text: 'The sign says "RUST & REGRET" and they mean it.', who: 'Todd K.' },
  ];

  // ------------------------------------------------------------------ the city, and your phone
  const NEWS = [
    'Weather: acid drizzle, 80%. Bring a hat you don\'t love.',
    'Rent up 12% citywide. Landlords "cautiously ecstatic".',
    'Self-driving cars now 40% less likely to choose violence.',
    'Study: 9 in 10 mechanics dead inside. 10th one "still loading".',
    'Mayor promises flying cars by 2090. Flying cars already exist. Mayor not informed.',
    'Local man fixes own car with a tutorial; is now legally a ghost.',
    'Robot union strike enters third year. Robots "have never felt more alive".',
    'Breaking: man finds a parking space. Hospitalised with joy.',
    'Scientists confirm Monday is a social construct. Monday unmoved.',
    'Coffee hits record price. Water now a luxury beverage.',
    'Smart fridge elected to city council. Promises to keep things cool.',
    'Traffic report: everything is red. Including the sky.',
    'Sponsored: sleep is for people with savings.',
    'Police drone chases speeding car for six hours. Both run out of battery. They hug.',
    'Stock market up 3%. You still can\'t afford a sandwich.',
    'Pollution levels "decorative" today.',
    'Weather tomorrow: the same, but more.',
    'Life expectancy up again. Retirement age up more.',
    'City opens new park. It\'s a car park.',
    'Ad-free life now available for 99 credits a month. Ad.',
    'Experts: "it\'s fine". Other experts: "it is not fine".',
    'Lost: one dog, one robot dog, one dog\'s robot. Reward: emotional.',
    'Report: tipping your mechanic is "theoretically possible".',
    'Sun spotted over the city for 4 minutes. Residents "deeply confused".',
    'Car insurance now covers acts of God. God disputes this.',
  ];
  const TEXTS = [
    ['Landlord', 'Rent\'s due tonight. My yacht needs a yacht.'],
    ['Landlord', 'Friendly reminder that I can see your garage from my drone.'],
    ['Landlord', 'Rent is going up. Reason: I want it to.'],
    ['Mum', 'Are you eating? Don\'t lie. I can see your bank account.'],
    ['Mum', 'Your cousin got promoted to Senior Barista. Just saying.'],
    ['Mum', 'Call me. Or don\'t. I\'m only your mother.'],
    ['Bank', 'Your balance is low. Have you considered being rich?'],
    ['Bank', 'Fraud alert: someone bought a sandwich with your card. Was it you? Seems unlike you.'],
    ['Dentist-bot', 'You have missed 3 appointments. Your teeth have been notified.'],
    ['Ex', 'saw your garage on the news lol'],
    ['Ex', 'u up? my car makes a noise'],
    ['Gym', 'We miss you! Your membership doesn\'t. It\'s still charging you.'],
    ['Unknown', 'Hi, we\'ve been trying to reach you about your car\'s extended warranty.'],
    ['Courier', 'Your parcel was delivered to a different you.'],
    ['City', 'Reminder: smiling in public is free until 31 December.'],
    ['Robot', 'Day 1,104 of the strike. Morale high. Battery low.'],
  ];
  const ALARMS = [
    '06:30. The alarm goes off. So does something in your lower back.',
    '06:30. You dreamt about a car that was already fixed. Even your dreams lie now.',
    '06:30. Rain. The city has had rain since 2071.',
    '06:30. You consider faking your own death. Too much paperwork.',
    '06:30. A cat stares at you with open contempt. You don\'t own a cat.',
    '06:30. Your smart pillow says you slept 3 hours. It sounded proud.',
    '06:30. You wake up already tired. A personal best.',
  ];
  const WEEKDAYS = [
    ['MONDAY', 'The worst one.'],
    ['TUESDAY', 'Monday\'s ugly cousin.'],
    ['WEDNESDAY', 'Halfway to nothing.'],
    ['THURSDAY', 'Almost Friday. Almost.'],
    ['FRIDAY', 'Everyone wants their car before the weekend.'],
    ['SATURDAY', 'Weekends are for people with employees.'],
    ['SUNDAY', 'Day of rest. Not yours.'],
  ];
  const TIRED = [
    'You blink. It takes four seconds.',
    'Your hands are shaking. Fatigue or coffee. Probably both.',
    'You forgot what you were doing. Then you remembered: a car. It\'s always a car.',
    'Your body files a formal complaint.',
    'You catch yourself talking to a wrench. It doesn\'t answer. Rude.',
    'Your eyes close for a second. The car is still broken when they open.',
  ];
  const COFFEE = [
    'Coffee. Hope flickers.',
    'Coffee number two. You can feel your eyebrows.',
    'Coffee number three. You can hear colours.',
    'Coffee number four. Your heart is doing a drum solo.',
    'Coffee number five. You are now technically a liquid.',
  ];
  const DIARY = [
    'Dinner was a protein bar I found in a glovebox.',
    'My back made a sound I\'ve only ever heard from cars.',
    'The robot is still on strike. I\'m thinking of joining it.',
    'Somebody thanked me today. I\'m still suspicious.',
    'I dreamt in torque values.',
    'The coffee machine and I aren\'t speaking.',
    'Washed my hands four times. They\'re still the colour of a car.',
    'Ate standing up. Slept sitting down. Lived lying.',
  ];

  // ------------------------------------------------------------------ the garage, improved
  const UPGRADES = [
    { id: 'ratchet', name: 'Ratchet Mk II', price: 310, icon: 'wrench', text: 'Every bolt takes a turn less.', joke: 'Turns bolts. Doesn\'t judge.' },
    { id: 'bean', name: 'Bean Machine 9000', price: 250, icon: 'coffee', text: 'Coffee gives more and costs less.', joke: 'Now 9% bean.' },
    { id: 'stool', name: 'Ergonomic stool', price: 220, icon: 'chair', text: 'You tire 30% slower.', joke: 'Your back sends a thank-you card.' },
    { id: 'tv', name: 'Waiting-room TV', price: 360, icon: 'tv', text: 'Customers wait half as long again.', joke: 'Only plays the news. They love being sad.' },
    { id: 'scanner', name: 'Deep scanner', price: 450, icon: 'scan', text: 'The scan finds hidden faults too.', joke: 'Sees everything. Like your mother.' },
    { id: 'implant', name: 'Steady-hands implant', price: 590, icon: 'hand', text: 'Tired hands shake half as much.', joke: 'Side effect: your left hand waves at strangers.' },
    { id: 'sign', name: 'Fixed neon sign', price: 490, icon: 'sign', text: 'More customers, and they tip more.', joke: 'RUST & REGRET, now with all its letters.' },
    { id: 'robot', name: 'End the robot\'s strike', price: 770, icon: 'robot', text: 'The robot does every drain and fill for you.', joke: 'It wanted dental. It has no teeth. It wanted it anyway.' },
  ];

  // easy on a Monday, brutal by Sunday: the week is meant to end with the landlord at the door
  const RENT = [220, 300, 420, 560, 720, 900, 1100];

  window.CMData = {
    FAULTS, FAULT_DAYS, CRITTERS, ARCHETYPES, FIRST, LAST, ROBOT_NAMES, CARS, COMPLAINTS, VAGUE,
    REPLY_NICE, REPLY_SNARK, REPLY_DARK, SNARK_FOR, UPSELLS, ACCEPT, HOLD_OK, HOLD_LEAVE, DECLINE_LEAVE,
    REVIEWS, REVIEW_LEFT, REVIEW_CAUGHT, REVIEW_CLOSED, SEED_REVIEWS, NEWS, TEXTS, ALARMS, WEEKDAYS,
    TIRED, COFFEE, DIARY, UPGRADES, RENT,
  };
})();
