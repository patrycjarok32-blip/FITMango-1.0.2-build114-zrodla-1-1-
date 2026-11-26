package com.example.ui.components

/**
 * Manually mapped exercise media from yuhonas/free-exercise-db (Unlicense).
 * Images are two real stills supplied by the upstream dataset. They are not
 * certified technique instruction or three separate motion frames.
 * Source: https://github.com/yuhonas/free-exercise-db
 */
internal object CuratedExercisePhotos {
    private const val BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
    private val images = mapOf(
        "Cable kickback" to listOf("One-Legged_Cable_Kickback/0.jpg", "One-Legged_Cable_Kickback/1.jpg"),
        "Mostek biodrowy na jednej nodze" to listOf("Single_Leg_Glute_Bridge/0.jpg", "Single_Leg_Glute_Bridge/1.jpg"),
        "Glute bridge na jednej nodze" to listOf("Single_Leg_Glute_Bridge/0.jpg", "Single_Leg_Glute_Bridge/1.jpg"),
        "Wyprost biodra z gumą" to listOf("Hip_Extension_with_Bands/0.jpg", "Hip_Extension_with_Bands/1.jpg"),
        "Wyprost biodra na wyciągu" to listOf("One-Legged_Cable_Kickback/0.jpg", "One-Legged_Cable_Kickback/1.jpg"),
        "Przysiad" to listOf("Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"),
        "Suwnica na nogi" to listOf("Leg_Press/0.jpg", "Leg_Press/1.jpg"),
        "Wykroki" to listOf("Dumbbell_Lunges/0.jpg", "Dumbbell_Lunges/1.jpg"),
        "Prostowanie nóg" to listOf("Leg_Extensions/0.jpg", "Leg_Extensions/1.jpg"),
        "Uginanie nóg" to listOf("Lying_Leg_Curls/0.jpg", "Lying_Leg_Curls/1.jpg"),
        "Hip thrust" to listOf("Barbell_Hip_Thrust/0.jpg", "Barbell_Hip_Thrust/1.jpg"),
        "Martwy ciąg rumuński" to listOf("Romanian_Deadlift/0.jpg", "Romanian_Deadlift/1.jpg"),
        "Odwodzenie nóg" to listOf("Thigh_Abductor/0.jpg", "Thigh_Abductor/1.jpg"),
        "Glute bridge" to listOf("Barbell_Glute_Bridge/0.jpg", "Barbell_Glute_Bridge/1.jpg"),
        "Wiosłowanie hantlami" to listOf("One-Arm_Dumbbell_Row/0.jpg", "One-Arm_Dumbbell_Row/1.jpg"),
        "Ściąganie drążka" to listOf("Wide-Grip_Lat_Pulldown/0.jpg", "Wide-Grip_Lat_Pulldown/1.jpg"),
        "Podciąganie" to listOf("Pullups/0.jpg", "Pullups/1.jpg"),
        "Wyciskanie na ławce" to listOf("Barbell_Bench_Press_-_Medium_Grip/0.jpg", "Barbell_Bench_Press_-_Medium_Grip/1.jpg"),
        "Pompki" to listOf("Pushups/0.jpg", "Pushups/1.jpg"),
        "Wyciskanie hantli" to listOf("Dumbbell_Shoulder_Press/0.jpg", "Dumbbell_Shoulder_Press/1.jpg"),
        "Unoszenie bokiem" to listOf("Side_Lateral_Raise/0.jpg", "Side_Lateral_Raise/1.jpg"),
        "Uginanie na biceps" to listOf("Barbell_Curl/0.jpg", "Barbell_Curl/1.jpg"),
        "Prostowanie na triceps" to listOf("Triceps_Pushdown/0.jpg", "Triceps_Pushdown/1.jpg"),
        "Plank" to listOf("Plank/0.jpg", "Plank/1.jpg"),
        "Dead bug" to listOf("Dead_Bug/0.jpg", "Dead_Bug/1.jpg"),
        "Przysiad goblet" to listOf("Goblet_Squat/0.jpg", "Goblet_Squat/1.jpg"),
        "Przysiad bułgarski" to listOf("Split_Squat_with_Dumbbells/0.jpg", "Split_Squat_with_Dumbbells/1.jpg"),
        "Przysiad Smitha" to listOf("Smith_Machine_Squat/0.jpg", "Smith_Machine_Squat/1.jpg"),
        "Step-up" to listOf("Dumbbell_Step_Ups/0.jpg", "Dumbbell_Step_Ups/1.jpg"),
        "Wykroki chodzone" to listOf("Barbell_Walking_Lunge/0.jpg", "Barbell_Walking_Lunge/1.jpg"),
        "Zakroki" to listOf("Dumbbell_Rear_Lunge/0.jpg", "Dumbbell_Rear_Lunge/1.jpg"),
        "Wspięcia na palce stojąc" to listOf("Standing_Calf_Raises/0.jpg", "Standing_Calf_Raises/1.jpg"),
        "Wspięcia na palce siedząc" to listOf("Seated_Calf_Raise/0.jpg", "Seated_Calf_Raise/1.jpg"),
        "Kickback na wyciągu" to listOf("One-Legged_Cable_Kickback/0.jpg", "One-Legged_Cable_Kickback/1.jpg"),
        "Martwy ciąg klasyczny" to listOf("Barbell_Deadlift/0.jpg", "Barbell_Deadlift/1.jpg"),
        "Martwy ciąg sumo" to listOf("Sumo_Deadlift/0.jpg", "Sumo_Deadlift/1.jpg"),
        "Wiosłowanie sztangą" to listOf("Bent_Over_Barbell_Row/0.jpg", "Bent_Over_Barbell_Row/1.jpg"),
        "Wiosłowanie na maszynie" to listOf("Seated_Cable_Rows/0.jpg", "Seated_Cable_Rows/1.jpg"),
        "Wiosłowanie na wyciągu siedząc" to listOf("Seated_Cable_Rows/0.jpg", "Seated_Cable_Rows/1.jpg"),
        "Face pull" to listOf("Face_Pull/0.jpg", "Face_Pull/1.jpg"),
        "Pullover na wyciągu" to listOf("Straight-Arm_Pulldown/0.jpg", "Straight-Arm_Pulldown/1.jpg"),
        "Wyciskanie hantli na ławce" to listOf("Dumbbell_Bench_Press/0.jpg", "Dumbbell_Bench_Press/1.jpg"),
        "Wyciskanie na skosie dodatnim" to listOf("Barbell_Incline_Bench_Press_-_Medium_Grip/0.jpg", "Barbell_Incline_Bench_Press_-_Medium_Grip/1.jpg"),
        "Rozpiętki z hantlami" to listOf("Dumbbell_Flyes/0.jpg", "Dumbbell_Flyes/1.jpg"),
        "Rozpiętki na maszynie" to listOf("Butterfly/0.jpg", "Butterfly/1.jpg"),
        "Rozpiętki na bramie" to listOf("Cable_Crossover/0.jpg", "Cable_Crossover/1.jpg"),
        "Wyciskanie żołnierskie" to listOf("Standing_Military_Press/0.jpg", "Standing_Military_Press/1.jpg"),
        "Arnold press" to listOf("Arnold_Dumbbell_Press/0.jpg", "Arnold_Dumbbell_Press/1.jpg"),
        "Unoszenie hantli w przód" to listOf("Front_Dumbbell_Raise/0.jpg", "Front_Dumbbell_Raise/1.jpg"),
        "Odwrotne rozpiętki" to listOf("Reverse_Flyes/0.jpg", "Reverse_Flyes/1.jpg"),
        "Reverse pec deck" to listOf("Reverse_Machine_Flyes/0.jpg", "Reverse_Machine_Flyes/1.jpg"),
        "Uginanie młotkowe" to listOf("Hammer_Curls/0.jpg", "Hammer_Curls/1.jpg"),
        "Uginanie na modlitewniku" to listOf("Preacher_Curl/0.jpg", "Preacher_Curl/1.jpg"),
        "Uginanie na wyciągu" to listOf("Cable_Hammer_Curls_-_Rope_Attachment/0.jpg", "Cable_Hammer_Curls_-_Rope_Attachment/1.jpg"),
        "Uginanie koncentracyjne" to listOf("Concentration_Curls/0.jpg", "Concentration_Curls/1.jpg"),
        "Francuskie wyciskanie" to listOf("Lying_Triceps_Press/0.jpg", "Lying_Triceps_Press/1.jpg"),
        "Prostowanie tricepsa nad głową" to listOf("Standing_Dumbbell_Triceps_Extension/0.jpg", "Standing_Dumbbell_Triceps_Extension/1.jpg"),
        "Dipy na poręczach" to listOf("Dips_-_Triceps_Version/0.jpg", "Dips_-_Triceps_Version/1.jpg"),
        "Russian twist" to listOf("Russian_Twist/0.jpg", "Russian_Twist/1.jpg"),
        "Side plank" to listOf("Side_Bridge/0.jpg", "Side_Bridge/1.jpg"),
        "Pallof press" to listOf("Pallof_Press/0.jpg", "Pallof_Press/1.jpg"),
        "Farmer walk" to listOf("Farmers_Walk/0.jpg", "Farmers_Walk/1.jpg"),
        "Unoszenie kolan w zwisie" to listOf("Hanging_Leg_Raise/0.jpg", "Hanging_Leg_Raise/1.jpg"),
    )

    private fun key(value: String) = java.text.Normalizer.normalize(value.trim().lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFC).replace(Regex("\\s+"), " ")
    private val normalizedImages = images.mapKeys { key(it.key) }

    private val repdb = mapOf(
        "Hack squat" to "hack-squat",
        "Pompki na kolanach" to "knee-push-ups",
        "Bird dog" to "bird-dog",
        "Pallof press" to "cable-pallof-press",
        "Kickback na wyciągu" to "cable-kickback",
        "Wyprost biodra na wyciągu" to "cable-kickback",
        "Wyprost biodra w klęku" to "glute-kickback",
        "Hip thrust z gumą" to "banded-hip-thrust",
        "Hip thrust z hantlem" to "dumbbell-hip-thrust",
        "Hip thrust na Smithie" to "smith-machine-hip-thrust",
    ).mapKeys { key(it.key) }
    private val repPairs = """90-90-switch
ab-wheel-rollout
archer-pull-ups
archer-push-ups
arnold-press
assisted-dips
assisted-pull-ups
back-extension
balance-dome-crunch
balance-dome-glute-bridge
balance-dome-push-up
balance-dome-squat
balance-dome-step-up
ball-leg-curl
ball-pike
band-assisted-pull-ups
band-pull-apart
band-pull-through
band-shoulder-external-rotation
banded-clamshell
banded-fire-hydrant
banded-glute-bridge
banded-good-morning
banded-hip-thrust
banded-kneeling-hip-thrust
banded-lateral-walk
banded-overhead-press
banded-pallof-press
banded-push-up
banded-romanian-deadlift
banded-seated-hip-abduction
banded-squat
banded-standing-curl
banded-standing-hip-abduction
banded-standing-hip-adduction
banded-standing-row
banded-sumo-walk
banded-terminal-knee-extension
barbell-ab-rollout
barbell-box-squat
barbell-bulgarian-split-squat
barbell-calf-raise
barbell-curl
barbell-front-raise
barbell-glute-bridge
barbell-lunge
barbell-overhead-extension
barbell-preacher-curl
barbell-pullover
barbell-rear-delt-row
barbell-reverse-lunge
barbell-row
barbell-wrist-curl
battle-rope-double-slam
bear-crawl
behind-the-back-barbell-shrug
behind-the-neck-lat-pulldown
behind-the-neck-press
behind-the-neck-pull-ups
bench-dips
bench-leg-pull-in
bench-press
bench-pull
bent-arm-barbell-pullover
bent-arm-ez-bar-pullover
bent-knee-copenhagen-raises
bent-over-db-row
bent-over-ez-bar-row
bicep-curl
bicycle-crunch
bird-dog
bodyweight-calf-raise
bodyweight-good-morning
bodyweight-lateral-raise
bodyweight-overhead-press
bodyweight-reverse-lunge
bodyweight-squat
box-jump
box-squat
bulgarian-split-squat
cable-bent-over-row
cable-chest-press
cable-crunch
cable-curl
cable-external-rotation
cable-fly
cable-front-raise
cable-hammer-curl
cable-kickback
cable-lateral-raise
cable-overhead-tricep-extension
cable-pallof-press
cable-pull-through
cable-reverse-fly
cable-tricep-kickback
cable-upright-row
cable-wrist-curl
captains-chair-knee-raise
captains-chair-leg-raise
cheat-curl
chest-press-machine
chest-supported-db-row
chest-supported-dumbbell-shrug
chest-supported-kettlebell-row
chest-supported-machine-row
chest-supported-smith-machine-row
chin-ups
clamshells
clap-push-ups
clean
clean-and-jerk
close-grip-barbell-curl
close-grip-bench-press
close-grip-db-bench-press
close-grip-ez-bar-bench-press
close-grip-ez-bar-curl
close-grip-incline-bench
close-grip-lat-pulldown
close-grip-pull-ups
close-grip-push-ups
close-stance-leg-press
cocoons
commando-pull-up
concentration-curl
copenhagen-plank-raises
cossack-squat
crab-dips
cross-body-crunch
cross-body-hammer-curl
crunches
db-bench-press
db-fly
db-goblet-squat
db-kickstand-deadlift
db-lunge
db-preacher-curl
db-pullover
db-reverse-curl
db-reverse-wrist-curl
db-shrug
db-skull-crusher
db-somersault-squat
db-squat
db-step-up-to-balance
db-sumo-squat
db-svend-press
dead-bug
deadlift
decline-bench-press
decline-bench-press-barbell
decline-bench-press-ez-bar
decline-crunch
decline-db-fly
decline-push-up
deficit-deadlift
deficit-push-ups
diamond-push-ups
dips
donkey-calf-raise
double-db-kickstand-deadlift
double-kettlebell-bicep-curl
double-kettlebell-clean
double-kettlebell-clean-and-press
double-kettlebell-dead-clean
double-kettlebell-dead-split-snatch
double-kettlebell-jerk
double-kettlebell-overhead-press
double-kettlebell-push-press
double-kettlebell-rear-delt-row
double-kettlebell-row
double-kettlebell-split-jerk
double-kettlebell-swing-snatch
downward-dog-knee-tuck
downward-dog-pedal
downward-dog-to-knee-drive
downward-dog-to-low-lunge
downward-dog-to-plank
downward-dog-to-upward-dog
drag-curl
dragon-flag
dumbbell-bench-pull
dumbbell-calf-raise
dumbbell-clean-and-press
dumbbell-curl-and-press
dumbbell-deadlift
dumbbell-face-pull
dumbbell-floor-press
dumbbell-front-raise
dumbbell-front-squat
dumbbell-hip-thrust
dumbbell-pistol-squat
dumbbell-push-press
dumbbell-reverse-fly
dumbbell-romanian-deadlift
dumbbell-shoulder-press
dumbbell-side-bend
dumbbell-snatch
dumbbell-split-squat
dumbbell-tricep-extension
dumbbell-upright-row
dumbbell-windmill
dumbbell-wrist-curl
ez-bar-bench-press
ez-bar-curl
ez-bar-front-raise
ez-bar-lying-tricep-extension
ez-bar-overhead-extension
ez-bar-pullover
ez-bar-reverse-curl
ez-bar-reverse-grip-row
ez-bar-romanian-deadlift
ez-bar-shrug
ez-bar-spider-curl
ez-bar-upright-row
ez-bar-wrist-curl
face-pull
fire-hydrant
floor-ez-bar-press
floor-kettlebell-pullover
floor-press
flutter-kicks
front-squat
glute-bridge
glute-kickback
goblet-squat
good-morning
hack-squat
hack-squat-calf-raise
half-kneeling-hip-flexor-rock
hammer-curl
handstand-push-ups
hang-clean
hang-power-clean
hang-snatch
hanging-knee-raise
hanging-leg-raise
hanging-pike
heel-elevated-squat
heel-touches
hex-bar-deadlift
high-foot-leg-press
hip-abduction
hip-adduction
hip-thrust
horizontal-leg-press
inchworm
incline-bench-ez-bar-press
incline-bench-press
incline-db-curl
incline-db-press
incline-dumbbell-fly
incline-hammer-curl
incline-push-ups
inverted-row
jackknife-sit-up
jefferson-curl
jump-squat
jumping-jacks
kb-step-up-to-balance
kettlebell-bulgarian-split-squat
kettlebell-close-grip-floor-press
kettlebell-concentration-curl
kettlebell-curl
kettlebell-deadlift
kettlebell-floor-press
kettlebell-goblet-lunge
kettlebell-halo
kettlebell-hammer-curl
kettlebell-hip-thrust
kettlebell-kickstand-deadlift
kettlebell-lunge-press
kettlebell-offset-reverse-lunge-and-press
kettlebell-overhead-tricep-extension
kettlebell-pistol-squat
kettlebell-pullover
kettlebell-reverse-lunge
kettlebell-reverse-wrist-curl
kettlebell-rotational-lunge
kettlebell-russian-twist
kettlebell-shrug
kettlebell-single-leg-deadlift
kettlebell-skull-crusher
kettlebell-squat
kettlebell-sumo-deadlift
kettlebell-sumo-high-pull
kettlebell-svend-press
kettlebell-swing
kettlebell-swing-clean
kettlebell-tibialis-raise
kettlebell-turkish-get-ups
kettlebell-windmills
kettlebell-wrist-curl
knee-push-ups
kneeling-cable-row
landmine-press
lat-pulldown
lateral-leg-swing
lateral-raise
leg-curl
leg-extension
leg-press
leg-swing-front-to-back
low-lunge-to-half-split
lunge
lying-leg-raise
lying-tricep-extension
machine-back-extension
machine-bicep-curl
machine-calf-raise
machine-chest-fly
machine-preacher-curl
machine-reverse-fly
machine-seated-crunch
machine-shoulder-press
machine-triceps-extension
medicine-ball-side-throw
medicine-ball-slam
mountain-climbers
muscle-snatch
muscle-ups
negative-pull-ups
neutral-grip-pull-ups
nordic-hamstring-curl
ohp
one-arm-dumbbell-preacher-curl
one-arm-dumbbell-push-press
one-arm-dumbbell-shoulder-press
one-arm-dumbbell-swing
one-arm-kettlebell-bicep-curl
one-arm-kettlebell-bottoms-up-press
one-arm-kettlebell-floor-glute-bridge-press
one-arm-kettlebell-floor-press
one-arm-kettlebell-front-squat
one-arm-kettlebell-push-press
one-arm-kettlebell-row
one-arm-kettlebell-shoulder-press
one-arm-kettlebell-swing
one-arm-kettlebell-tricep-kickback
one-arm-landmine-press
one-arm-lat-pulldown
one-arm-seated-cable-row
one-arm-single-leg-dumbbell-romanian-deadlift
one-arm-single-leg-kettlebell-romanian-deadlift
overhead-squat
overhead-tricep-extension
pause-deadlift
pause-pull-up
pause-squat
paused-bench-press
paused-incline-bench-press
paused-ohp
pec-deck
pendlay-row
pike-push-ups
pilates-kneeling-side-kick
pilates-leg-pull-back
pilates-leg-pull-front
pilates-roll-down
pilates-roll-over
pilates-saw
pilates-side-bend
pilates-spine-stretch-forward
pilates-spine-twist
pistol-squat
plank-knee-tuck
plate-loaded-donkey-calf-raise
plate-loaded-glute-drive
plate-loaded-lateral-raise
plate-loaded-seated-dip-machine
plate-loaded-shrug
plate-pinch
plate-pullover
plyo-lunge
plyo-push-up
preacher-curl
preacher-hammer-curl
pseudo-planche-push-ups
pull-up
push-jerk
push-press
push-up
rack-pull
rear-delt-fly
renegade-row
reverse-crunches
reverse-curl
reverse-grip-bent-over-row
reverse-grip-lat-pulldown
reverse-lunge
reverse-nordic-curl
reverse-plank-dips
reverse-tabletop-hip-pulses
ring-dips
ring-face-pull
ring-muscle-up
ring-push-up
ring-row
rings-inverted-row
romanian-deadlift
rowing-machine
russian-twist
scapular-pull-ups
scissor-kicks
seated-barbell-overhead-press
seated-cable-row
seated-calf-raise
seated-db-press
seated-dip-machine
seated-dumbbell-curl
seated-dumbbell-lateral-raise
seated-dumbbell-tricep-extension
seated-leg-curl
seated-smith-machine-shoulder-press
shrimp-squat
shrug
side-lunge
side-lying-hip-abduction
side-lying-hip-adduction
side-lying-lateral-raise
side-plank-leg-lift
single-arm-chest-supported-dumbbell-row
single-arm-db-row
single-arm-dumbbell-bench-press
single-arm-dumbbell-overhead-tricep-extension
single-arm-hammer-curl
single-arm-machine-shoulder-press
single-arm-plate-loaded-lateral-raise
single-arm-tricep-pushdown
single-db-svend-press
single-leg-box-squat
single-leg-calf-raise
single-leg-extension
single-leg-glute-bridge
single-leg-hip-thrust
single-leg-lying-leg-curl
single-leg-press
single-leg-romanian-deadlift
sissy-squat
sit-ups
ski-erg
skull-crusher
sled-row
smith-machine-bench-press
smith-machine-bent-over-row
smith-machine-bulgarian-split
smith-machine-calf-raise
smith-machine-decline-bench-press
smith-machine-front-squat
smith-machine-good-morning
smith-machine-hip-thrust
smith-machine-incline-bench-press
smith-machine-rdl
smith-machine-reverse-grip-bent-over-row
smith-machine-reverse-lunge
smith-machine-shoulder-press
smith-machine-shrug
smith-machine-split-squat
smith-machine-squat
smith-machine-upright-row
snatch
spider-curl
spiderman-push-up
split-jerk
split-squat
spoto-press
squat
squat-pulse
stability-ball-hip-bridge
stability-ball-knee-tuck
stability-ball-push-up
stability-ball-push-up-hands-on-ball
stability-ball-wall-squat
standing-calf-raise
standing-forward-fold-to-half-lift
standing-hip-abduction
standing-side-bend-flow
step-up-to-balance
step-ups
stiff-leg-deadlift
straight-arm-pulldown
straight-bar-cable-front-raise
straight-bar-dips
strict-curl
sumo-deadlift
sumo-squat
superman
supine-windshield-wipers
svend-press
t-bar-row
thread-the-needle-flow
thruster
toes-to-bar
tricep-kickback
tricep-pushdown
trx-bicep-curl
trx-chest-press
trx-face-pull
trx-hamstring-curl
trx-lunge
trx-pistol-squat
trx-row
trx-squat
trx-tricep-extension
trx-y-fly
upright-row
v-bar-lat-pulldown
v-bar-tricep-pushdown
v-ups
walking-lunge
wall-push-ups
weighted-dips
weighted-pull-up
weighted-wall-crunch
wide-grip-bench-press
wide-grip-pull-ups
wide-grip-push-ups
wide-grip-seated-cable-row
wide-stance-leg-press
worlds-greatest-stretch
wrist-curl
wrist-roller
zercher-squat
zottman-curl""".lineSequence().toSet()
    private val repSingles = """90-90-hold
air-bike
back-lever
balance-dome-plank
balance-dome-single-leg-stand
balance-dome-squat-hold
balance-dome-stand
banded-adductor-stretch
banded-ankle-stretch
banded-calf-stretch
banded-chest-stretch
banded-figure-4-stretch
banded-hamstring-stretch
banded-it-band-stretch
banded-lat-stretch
banded-rear-delt-stretch
banded-shoulder-stretch
banded-triceps-stretch
barbell-isometric-squat-hold
barbell-zercher-hold
barbell-zercher-squat-hold
battle-ropes
bear-plank-hold
bench-adductor-stretch
bench-ankle-stretch
bench-bulgarian-split-stretch
bench-calf-stretch
bench-chest-stretch
bench-childs-pose
bench-couch-stretch
bench-decline-plank-hold
bench-figure-4-glute-stretch
bench-hamstring-stretch
bench-hollow-hold
bench-lat-stretch
bird-dog-hold
boat-pose
bow-pose
burpees
butterfly-stretch
calf-raise-hold
camel-pose
cat-cow
cat-stretch
chair-pose
childs-pose
chin-tuck-hold
clamshells-hold
cobra-stretch
copenhagen-plank
cow-face-pose
crescent-lunge
cross-body-shoulder-stretch
crow-pose
cycling
dancer-pose
db-overhead-carry
dead-bug-hold
dead-hang
deep-squat-hold
dolphin-pose
doorway-chest-stretch
double-db-overhead-carry
double-kettlebell-overhead-carry
downward-dog
dumbbell-farmers-walk
eagle-pose
easy-pose
elliptical-trainer
extended-side-angle
fish-pose
flexed-arm-hang
frog-stretch
front-lever
garland-pose
glute-bridge-hold
glute-kickback-hold
half-moon-pose
handstand-hold
hanging-l-sit
hanging-tuck-hold
happy-baby
head-to-knee-pose
heel-flicks
heel-to-toe-walk
hero-pose
high-knees
high-plank
hollow-body-hold
human-flag
hyperextension-hold-bench
incline-plank-hold
incline-treadmill-walk
isometric-neck-side
isometric-split-squat
jump-rope
kettlebell-bottoms-up-hold
kettlebell-curl-iso-hold
kettlebell-farmers-walk
kettlebell-overhead-carry
kettlebell-overhead-hold
kettlebell-rack-hold
knee-to-chest-stretch
kneeling-hip-flexor-stretch
kneeling-wrist-stretch
l-sit
landmine-single-arm-overhead-hold
lean-back-90-90-hold
legs-up-the-wall
lizard-stretch
locust-pose
low-lunge
lying-quad-stretch
mountain-pose
neck-side-stretch
overhead-triceps-stretch
pigeon-stretch
planche
planche-lean-floor
plank
plow-pose
puppy-pose
pyramid-pose
reclined-butterfly
reverse-plank
reverse-tabletop-hold
revolved-chair-pose
revolved-crescent-lunge
ring-dead-hang
ring-l-sit
ring-plank
ring-support-hold
rope-climb
running
sandbag-carry
savasana
seated-forward-fold
seated-spinal-twist
seated-straddle-stretch
side-lying-hip-abduction-hold
side-lying-hip-adduction-hold
side-plank
side-plank-leg-lift-hold
single-arm-plank
single-leg-glute-bridge-hold
single-leg-hip-thrust-hold
single-leg-rdl-hold
six-inch-hold
sled-push
sphinx-pose
squat-hold
stair-climber
standing-adductor-stretch
standing-calf-stretch
standing-forward-fold
standing-quad-stretch
standing-side-bend
standing-split
stationary-bike
suitcase-carry
supine-spinal-twist
supported-shoulderstand
supported-single-leg-stand
tandem-stance
thoracic-bridge
thread-the-needle
three-legged-dog
torso-twists
treadmill-running
tree-pose
triangle-pose
triceps-dip-hold-bars
triceps-dip-hold-bench
trx-plank
trx-side-plank
upward-dog
v-sit
walking
wall-ball
wall-hamstring-stretch
wall-sit
warrior-one
warrior-three
warrior-two
wide-legged-forward-fold""".lineSequence().toSet()
    fun illustrationUrl(exercise: String, stage: Int): String? {
        val id = repdb[key(exercise)] ?: key(exercise).replace(" ", "-")
        if (id !in repPairs && id !in repSingles) return null
        val pose = when (stage) { 0, 1 -> "start"; 2, 3 -> "peak"; else -> return null }
        if (id in repSingles && pose == "peak") return null
        val frame = if (id in repSingles) "main" else pose
        return "https://raw.githubusercontent.com/RepDB/exercise-dataset/main/images/flat/$id-$frame.webp"
    }
    const val REPDB_CREDIT = "Exercise data by RepDB (repdb.co)"

    fun url(exercise: String, stage: Int): String? {
        val stills = normalizedImages[key(exercise)] ?: return illustrationUrl(exercise, stage)
        val index = when(stage) { 0, 1 -> 0; 2, 3 -> 1; else -> return null }
        return BASE + stills[index].split("/").joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
    }

    fun hasPhotos(exercise: String) = normalizedImages.containsKey(key(exercise)) || illustrationUrl(exercise, 1) != null
    fun coveredCount() = StrengthExerciseLibrary.exercises.count { hasPhotos(it.name) }
    const val CREDIT = "Źródło: free-exercise-db. Ujęcia poglądowe; sprawdź technikę."
}
