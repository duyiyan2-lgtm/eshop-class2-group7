import { collectRoleBindings } from './vehicleMaterialMap'
import { DEFAULT_CALIPER, VEHICLE_3D_MANIFEST as MANIFEST } from './vehicleModelManifest'

const COLOR_MS = 220
const DESKTOP_FRAME = {
  side: 0.065,
  top: 0.05,
  bottom: 0.14,
  center: 0.58,
  targetW: 0.872,
  minW: 0.845,
  maxW: 0.878,
}
const COMPACT_FRAME = {
  side: 0.07,
  top: 0.18,
  bottom: 0.14,
  center: 0.53,
  targetW: 0.80,
  minW: 0.76,
  maxW: 0.84,
}
const COMPACT_FIT = {
  side: 0.045,
  top: 0.10,
  bottom: 0.10,
}
const CAMERA_MS = 620

const isCompactStage = () => (
  typeof window !== 'undefined' && window.innerWidth < 1100
)

const currentFrame = () => (isCompactStage() ? COMPACT_FRAME : DESKTOP_FRAME)

const detectWebGL = () => {
  try {
    const canvas = document.createElement('canvas')
    return Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl'))
  } catch {
    return false
  }
}

const disposeObject = (object) => {
  object.traverse((child) => {
    if (child.geometry) child.geometry.dispose()
    const materials = Array.isArray(child.material) ? child.material : [child.material]
    materials.forEach((material) => {
      if (!material) return
      Object.values(material).forEach((value) => {
        if (value && value.isTexture) value.dispose()
      })
      material.dispose()
    })
  })
}

const makeContactShadow = (THREE) => {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 256
  const ctx = canvas.getContext('2d')
  const gradient = ctx.createRadialGradient(128, 128, 8, 128, 128, 124)
  gradient.addColorStop(0, 'rgba(18,28,38,0.38)')
  gradient.addColorStop(0.42, 'rgba(18,28,38,0.14)')
  gradient.addColorStop(1, 'rgba(18,28,38,0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, 256, 256)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  const mesh = new THREE.Mesh(
    new THREE.PlaneGeometry(1, 1),
    new THREE.MeshBasicMaterial({
      map: texture,
      transparent: true,
      depthWrite: false,
    }),
  )
  mesh.rotation.x = -Math.PI / 2
  mesh.position.y = 0.006
  mesh.renderOrder = -1
  return mesh
}

const boxCorners = (box, THREE) => ([
  new THREE.Vector3(box.min.x, box.min.y, box.min.z),
  new THREE.Vector3(box.min.x, box.min.y, box.max.z),
  new THREE.Vector3(box.min.x, box.max.y, box.min.z),
  new THREE.Vector3(box.min.x, box.max.y, box.max.z),
  new THREE.Vector3(box.max.x, box.min.y, box.min.z),
  new THREE.Vector3(box.max.x, box.min.y, box.max.z),
  new THREE.Vector3(box.max.x, box.max.y, box.min.z),
  new THREE.Vector3(box.max.x, box.max.y, box.max.z),
])

export const createVehicleScene = async (canvas, options = {}) => {
  if (!detectWebGL()) throw new Error('WEBGL_UNAVAILABLE')

  const THREE = await import('three')
  const { OrbitControls } = await import('three/examples/jsm/controls/OrbitControls.js')
  const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js')
  const { DRACOLoader } = await import('three/examples/jsm/loaders/DRACOLoader.js')
  const { KTX2Loader } = await import('three/examples/jsm/loaders/KTX2Loader.js')
  let MeshoptDecoder = null
  try {
    const meshopt = await import('three/examples/jsm/libs/meshopt_decoder.module.js')
    MeshoptDecoder = meshopt.MeshoptDecoder
  } catch {
    MeshoptDecoder = null
  }

  const reduceMotion = Boolean(options.reduceMotion)
  const onProgress = options.onProgress || (() => {})
  const onLost = options.onLost || (() => {})

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance',
  })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 0.86
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.setClearColor(0xb9cbdc, 1)

  const scene = new THREE.Scene()
  const backdrop = document.createElement('canvas')
  backdrop.width = 4
  backdrop.height = 512
  const backdropCtx = backdrop.getContext('2d')
  const backdropGrad = backdropCtx.createLinearGradient(0, 0, 0, 512)
  backdropGrad.addColorStop(0, '#b9cbdc')
  backdropGrad.addColorStop(0.48, '#8fa8be')
  backdropGrad.addColorStop(1, '#657f98')
  backdropCtx.fillStyle = backdropGrad
  backdropCtx.fillRect(0, 0, 4, 512)
  const backdropMap = new THREE.CanvasTexture(backdrop)
  backdropMap.colorSpace = THREE.SRGBColorSpace
  scene.background = backdropMap

  const camera = new THREE.PerspectiveCamera(31, 1, 0.08, 80)
  camera.position.set(5.2, 1.5, 5.6)

  const controls = new OrbitControls(camera, canvas)
  controls.enableDamping = !reduceMotion
  controls.dampingFactor = 0.08
  controls.enablePan = false
  controls.minPolarAngle = MANIFEST.framing.polarMin
  controls.maxPolarAngle = MANIFEST.framing.polarMax
  controls.target.set(0, 0.45, 0)
  controls.update()

  const hemi = new THREE.HemisphereLight(0xe8f1f8, 0x7b91a6, 0.56)
  const key = new THREE.DirectionalLight(0xfff6ee, 1.48)
  key.position.set(6.4, 5.6, 3.8)
  key.castShadow = true
  key.shadow.mapSize.set(2048, 2048)
  key.shadow.camera.near = 0.8
  key.shadow.camera.far = 24
  key.shadow.camera.left = -6.4
  key.shadow.camera.right = 6.4
  key.shadow.camera.top = 4.8
  key.shadow.camera.bottom = -3.6
  key.shadow.radius = 4
  key.shadow.bias = -0.00028
  const fill = new THREE.DirectionalLight(0xd5e3ef, 0.28)
  fill.position.set(-5.8, 2.1, 4.6)
  const rim = new THREE.DirectionalLight(0xe7eef5, 0.46)
  rim.position.set(-2.4, 5.2, -7.4)
  const bounce = new THREE.DirectionalLight(0xf4f7fb, 0.16)
  bounce.position.set(1.1, 0.55, 3.2)
  const cabinFill = new THREE.PointLight(0xffead7, 0, 2.8, 2)
  const cabinDash = new THREE.SpotLight(0xfff1e2, 0, 3.4, 0.72, 0.45, 1.6)
  cabinDash.castShadow = false
  scene.add(hemi, key, fill, rim, bounce, cabinFill, cabinDash, cabinDash.target)

  const floor = new THREE.Mesh(
    new THREE.CircleGeometry(28, 96),
    new THREE.ShadowMaterial({
      color: '#243140',
      opacity: 0.22,
      transparent: true,
      depthWrite: false,
    }),
  )
  floor.rotation.x = -Math.PI / 2
  floor.position.y = 0
  floor.receiveShadow = true
  floor.renderOrder = -2
  scene.add(floor)

  const contact = makeContactShadow(THREE)
  scene.add(contact)

  let raf = 0
  let disposed = false
  let dirty = true
  let animating = false
  let dragging = false
  let loopRunning = false
  let renderCount = 0
  let hidden = document.hidden
  let inView = true
  let modelRoot = null
  let bindings = null
  let wheelRoots = []
  let fitDistance = 6
  let lookTarget = new THREE.Vector3(0, 0.42, 0)
  let lastPreset = 'front45'
  let colorToken = 0
  let colorTimer = 0
  let cameraAnimToken = 0
  let resizeObserver
  let intersectObserver
  let envMap = null
  let interiorMode = false
  const glassDefaults = new Map()
  let interiorParts = { meshes: [], steering: [], seats: [], dash: [] }
  let demoCluster = null
  let interiorLook = { minAzimuth: -Infinity, maxAzimuth: Infinity, span: 1.2 }
  const EXTERIOR_FOV = 31
  const DRIVER_FOV = 54
  const CABIN_FOV = 68
  const isInteriorPreset = (id) => Boolean(MANIFEST.cameras[id || lastPreset]?.interior)
  const INTERIOR_NODE_RE = /interior|seat|steering|dashboard|cockpit|cabin|shifter|pedal/i
  const STEERING_RE = /steering/i
  const SEAT_RE = /seat/i
  const DASH_RE = /dashboard|chassisinterior|console|shifter/i

  const stopLoop = () => {
    if (raf) window.cancelAnimationFrame(raf)
    raf = 0
    loopRunning = false
  }

  const kick = () => {
    if (disposed) return
    dirty = true
    if (hidden || !inView) return
    if (!loopRunning) {
      loopRunning = true
      raf = window.requestAnimationFrame(loop)
    }
  }

  const renderOnce = () => {
    if (disposed) return
    renderer.render(scene, camera)
    renderCount += 1
  }

  const loop = () => {
    raf = 0
    if (disposed || hidden || !inView) {
      loopRunning = false
      return
    }
    let damping = false
    if (controls.enableDamping || controls.autoRotate) damping = Boolean(controls.update()) || controls.autoRotate
    if (animating || dirty || dragging || damping) {
      renderOnce()
      if (!animating && !dragging) dirty = false
    }
    if (animating || dragging || damping || dirty) {
      raf = window.requestAnimationFrame(loop)
      return
    }
    loopRunning = false
  }

  const onVisibility = () => {
    hidden = document.hidden
    if (hidden) stopLoop()
    else kick()
  }

  const onPointerDown = () => {
    dragging = true
    kick()
  }

  const onPointerMove = () => {
    if (dragging) kick()
  }

  const onPointerUp = () => {
    dragging = false
    kick()
  }

  const onContextLost = (event) => {
    event.preventDefault()
    onLost(new Error('WEBGL_CONTEXT_LOST'))
  }

  const placeCamera = (azimuth, polar, distance, target) => {
    camera.position.set(
      target.x + Math.sin(azimuth) * Math.sin(polar) * distance,
      target.y + Math.cos(polar) * distance,
      target.z + Math.cos(azimuth) * Math.sin(polar) * distance,
    )
    controls.target.copy(target)
    camera.lookAt(target)
    camera.updateMatrixWorld(true)
    camera.updateProjectionMatrix()
    controls.update()
  }

  const boxFits = (box, azimuth, polar, distance, target) => {
    const frame = isCompactStage() ? COMPACT_FIT : currentFrame()
    placeCamera(azimuth, polar, distance, target)
    const point = new THREE.Vector3()
    return boxCorners(box, THREE).every((corner) => {
      point.copy(corner).project(camera)
      if (point.z < -1 || point.z > 1) return false
      if (point.x < -1 + 2 * frame.side || point.x > 1 - 2 * frame.side) return false
      if (point.y < -1 + 2 * frame.bottom || point.y > 1 - 2 * frame.top) return false
      return true
    })
  }

  const distanceForView = (azimuth, polar, target) => {
    const box = new THREE.Box3().setFromObject(modelRoot)
    const sphere = box.getBoundingSphere(new THREE.Sphere())
    const vFov = (camera.fov * Math.PI) / 180
    const hFov = 2 * Math.atan(Math.tan(vFov / 2) * Math.max(camera.aspect, 0.01))
    const fitFov = Math.min(vFov, hFov)
    const sphereDistance = (sphere.radius * 1.16) / Math.max(Math.sin(fitFov / 2), 0.08)
    let low = sphere.radius * 1.05
    let high = Math.max(sphereDistance, sphere.radius * 2.2)
    while (!boxFits(box, azimuth, polar, high, target) && high < sphere.radius * 24) {
      high *= 1.18
    }
    for (let step = 0; step < 16; step += 1) {
      const mid = (low + high) / 2
      if (boxFits(box, azimuth, polar, mid, target)) high = mid
      else low = mid
    }
    return high * (isCompactStage() ? 1.0 : 1.03)
  }

  const panScreen = (dx, dy) => {
    const distance = camera.position.distanceTo(controls.target)
    const vFov = (camera.fov * Math.PI) / 180
    const hFov = 2 * Math.atan(Math.tan(vFov / 2) * Math.max(camera.aspect, 0.01))
    const worldX = dx * 2 * Math.tan(hFov / 2) * distance
    const worldY = dy * 2 * Math.tan(vFov / 2) * distance
    const right = new THREE.Vector3().setFromMatrixColumn(camera.matrixWorld, 0).normalize()
    camera.position.addScaledVector(right, -worldX)
    controls.target.addScaledVector(right, -worldX)
    camera.position.y += worldY
    controls.target.y += worldY
    camera.lookAt(controls.target)
    camera.updateMatrixWorld(true)
    controls.update()
  }

  const composeOnStage = () => {
    for (let step = 0; step < 8; step += 1) {
      const bounds = getVehicleScreenBounds()
      if (!bounds || !Number.isFinite(bounds.top) || bounds.height > 1.2 || bounds.top < -1) return
      const frame = currentFrame()
      const centerY = (bounds.top + bounds.bottom) / 2
      const centerX = (bounds.left + bounds.right) / 2
      let dy = frame.center - centerY
      const dx = 0.5 - centerX
      if (bounds.bottom + dy > (isCompactStage() ? 0.86 : 0.878)) {
        dy = (isCompactStage() ? 0.86 : 0.878) - bounds.bottom
      }
      if (isCompactStage() && bounds.top + dy < 0.18) {
        dy = 0.18 - bounds.top
      }
      if (Math.abs(dx) < 0.006 && Math.abs(dy) < 0.006) break
      panScreen(dx, dy)
    }
  }

  const scaleCameraDistance = (factor) => {
    const dir = camera.position.clone().sub(controls.target).multiplyScalar(factor)
    camera.position.copy(controls.target).add(dir)
    camera.lookAt(controls.target)
    camera.updateMatrixWorld(true)
    controls.update()
  }

  const nudgeToWidth = () => {
    const frame = currentFrame()
    for (let step = 0; step < 12; step += 1) {
      const bounds = getVehicleScreenBounds()
      if (!bounds || !Number.isFinite(bounds.width) || bounds.width < 0.05) return
      const clipped = isCompactStage()
        ? bounds.left < 0.06 || bounds.right > 0.94 || bounds.top < 0.175 || bounds.bottom > 0.885
        : bounds.left < 0.04 || bounds.right > 0.96 || bounds.top < 0.03 || bounds.bottom > 0.93
      if (clipped && bounds.width > frame.minW + 0.004) {
        scaleCameraDistance(1.02)
        composeOnStage()
        continue
      }
      if (bounds.width >= frame.minW && bounds.width <= frame.maxW && !clipped) break
      if (bounds.width < frame.minW) {
        const factor = Math.max(isCompactStage() ? 0.88 : 0.90, bounds.width / frame.targetW)
        if (Math.abs(1 - factor) < 0.002) break
        scaleCameraDistance(factor)
        composeOnStage()
        continue
      }
      if (bounds.width > frame.maxW) {
        const factor = Math.min(1.06, Math.max(1.01, bounds.width / frame.targetW))
        scaleCameraDistance(factor)
        composeOnStage()
      }
    }
  }

  const applyGrounding = () => {
    if (!modelRoot) return
    const box = new THREE.Box3().setFromObject(modelRoot)
    const size = box.getSize(new THREE.Vector3())
    const center = box.getCenter(new THREE.Vector3())
    modelRoot.position.x -= center.x
    modelRoot.position.z -= center.z
    modelRoot.position.y -= box.min.y + 0.004
    const grounded = new THREE.Box3().setFromObject(modelRoot)
    const groundedSize = grounded.getSize(new THREE.Vector3())
    lookTarget.set(0, groundedSize.y * 0.38, 0)
    contact.scale.set(groundedSize.x * 1.22, groundedSize.z * 1.48, 1)
    contact.position.set(0, 0.004, groundedSize.z * 0.02)
    return groundedSize
  }

  const unlockDistance = () => {
    controls.minDistance = 0.05
    controls.maxDistance = 120
    controls.minPolarAngle = 0.12
    controls.maxPolarAngle = Math.PI - 0.12
  }

  const collectInteriorParts = () => {
    interiorParts = { meshes: [], steering: [], seats: [], dash: [] }
    if (!modelRoot) return
    modelRoot.traverse((object) => {
      if (!object.isMesh) return
      const name = `${object.name || ''} ${object.parent?.name || ''}`
      if (!INTERIOR_NODE_RE.test(name)) return
      interiorParts.meshes.push(object)
      if (STEERING_RE.test(name)) interiorParts.steering.push(object)
      else if (SEAT_RE.test(name)) interiorParts.seats.push(object)
      else if (DASH_RE.test(name)) interiorParts.dash.push(object)
    })
  }

  const boxFromMeshes = (meshes) => {
    const box = new THREE.Box3()
    box.makeEmpty()
    meshes.forEach((mesh) => box.expandByObject(mesh))
    return box
  }

  const getInteriorBox = () => {
    const fromParts = boxFromMeshes(interiorParts.meshes)
    if (!fromParts.isEmpty()) return fromParts
    const interiorSet = new Set(bindings?.Interior || [])
    const fromMats = new THREE.Box3()
    fromMats.makeEmpty()
    modelRoot?.traverse((object) => {
      if (!object.isMesh) return
      const materials = Array.isArray(object.material) ? object.material : [object.material]
      if (materials.some((material) => interiorSet.has(material))) fromMats.expandByObject(object)
    })
    return fromMats
  }

  const applyExteriorControls = () => {
    controls.minPolarAngle = MANIFEST.framing.polarMin
    controls.maxPolarAngle = MANIFEST.framing.polarMax
    controls.minAzimuthAngle = -Infinity
    controls.maxAzimuthAngle = Infinity
    controls.minDistance = fitDistance * MANIFEST.framing.minDistanceScale
    controls.maxDistance = fitDistance * MANIFEST.framing.maxDistanceScale
    camera.fov = EXTERIOR_FOV
    camera.near = 0.08
    camera.updateProjectionMatrix()
  }

  const applyInteriorControls = (cabinSpan = interiorLook.span) => {
    const offset = camera.position.clone().sub(controls.target)
    const spherical = new THREE.Spherical().setFromVector3(offset)
    const span = Math.max(0.9, cabinSpan || 1.2)
    controls.minPolarAngle = Math.max(0.85, spherical.phi - 0.28)
    controls.maxPolarAngle = Math.min(Math.PI - 0.45, spherical.phi + 0.32)
    controls.minAzimuthAngle = spherical.theta - 0.62
    controls.maxAzimuthAngle = spherical.theta + 0.62
    controls.minDistance = Math.max(0.22, spherical.radius * 0.7)
    controls.maxDistance = Math.min(span * 0.62, Math.max(0.72, spherical.radius * 1.35))
    const variant = MANIFEST.cameras[lastPreset]?.variant || 'driver'
    camera.fov = variant === 'cabinWide' ? CABIN_FOV : DRIVER_FOV
    camera.near = 0.04
    camera.updateProjectionMatrix()
    if (variant === 'cabinWide') {
      controls.minAzimuthAngle = spherical.theta - 0.72
      controls.maxAzimuthAngle = spherical.theta + 0.72
    }
    interiorLook = {
      minAzimuth: controls.minAzimuthAngle,
      maxAzimuth: controls.maxAzimuthAngle,
      span,
      variant,
      fov: camera.fov,
    }
  }

  const drawDemoCluster = () => {
    if (!demoCluster) return
    const ctx = demoCluster.ctx
    const w = demoCluster.canvas.width
    const h = demoCluster.canvas.height
    ctx.fillStyle = '#0c1218'
    ctx.fillRect(0, 0, w, h)
    ctx.beginPath()
    for (let i = 0; i < 6; i += 1) {
      const a = (Math.PI / 6) + (i * Math.PI) / 3
      const x = w * 0.5 + Math.cos(a) * w * 0.46
      const y = h * 0.5 + Math.sin(a) * h * 0.46
      if (i === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    }
    ctx.closePath()
    ctx.fillStyle = '#15202b'
    ctx.fill()
    ctx.fillStyle = '#d7e6ef'
    ctx.font = '700 92px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('0', w * 0.5, h * 0.46)
    ctx.font = '500 24px sans-serif'
    ctx.fillStyle = '#8aa0b0'
    ctx.fillText('km/h', w * 0.5, h * 0.56)
    ctx.fillStyle = '#c9a227'
    ctx.font = '700 36px sans-serif'
    ctx.fillText('P', w * 0.34, h * 0.7)
    ctx.fillStyle = '#4ad27a'
    ctx.fillRect(w * 0.52, h * 0.64, 64, 14)
    ctx.fillStyle = '#1d2a33'
    ctx.fillRect(w * 0.52 + 50, h * 0.64, 14, 14)
    ctx.fillStyle = '#7f93a1'
    ctx.font = '600 16px sans-serif'
    ctx.fillText('COURSE DEMO', w * 0.5, h * 0.82)
    demoCluster.texture.needsUpdate = true
  }

  const cabinAxes = () => {
    const steerBox = boxFromMeshes(interiorParts.steering)
    const seatBox = boxFromMeshes(interiorParts.seats)
    const steerCenter = steerBox.isEmpty() ? new THREE.Vector3() : steerBox.getCenter(new THREE.Vector3())
    const forward = new THREE.Vector3(0, 0, 1)
    if (!seatBox.isEmpty() && !steerBox.isEmpty()) {
      forward.copy(steerCenter).sub(seatBox.getCenter(new THREE.Vector3()))
      forward.y = 0
      if (forward.lengthSq() < 0.002) forward.set(0, 0, 1)
      else forward.normalize()
    }
    const right = new THREE.Vector3().crossVectors(new THREE.Vector3(0, 1, 0), forward)
    if (right.lengthSq() < 0.001) right.set(1, 0, 0)
    else right.normalize()
    const up = new THREE.Vector3().crossVectors(forward, right).normalize()
    return { steerBox, seatBox, steerCenter, forward, right, up }
  }

  const hexGeometry = (radius) => {
    const geometry = new THREE.CircleGeometry(radius, 6)
    geometry.rotateZ(Math.PI / 6)
    return geometry
  }

  const rebuildClusterGeometry = (plateR, bezelR, screenR) => {
    if (!demoCluster) return
    demoCluster.plate.geometry.dispose()
    demoCluster.bezel.geometry.dispose()
    demoCluster.screen.geometry.dispose()
    demoCluster.plate.geometry = hexGeometry(plateR)
    demoCluster.bezel.geometry = hexGeometry(bezelR)
    demoCluster.screen.geometry = hexGeometry(screenR)
    demoCluster.plateRadius = plateR
    demoCluster.bezelRadius = bezelR
    demoCluster.screenRadius = screenR
  }

  let lastCavityScan = null

  const scanInstrumentCavity = () => {
    if (!interiorParts.steering.length) return null
    const { steerCenter, forward, right, up } = cabinAxes()
    const targets = (interiorParts.dash.length ? interiorParts.dash : interiorParts.meshes)
      .filter((mesh) => !String(mesh.name || '').includes('CourseDemo'))
    const radii = [0.05, 0.07, 0.09, 0.11, 0.13, 0.15]
    const byRadius = []
    const hits = []
    const scanOrigin = steerCenter.clone().addScaledVector(up, 0.028).addScaledVector(forward, 0.05)
    radii.forEach((radius) => {
      let count = 0
      for (let i = 0; i < 16; i += 1) {
        const angle = (i / 16) * Math.PI * 2
        const origin = scanOrigin.clone()
          .addScaledVector(right, Math.cos(angle) * radius)
          .addScaledVector(up, Math.sin(angle) * radius)
        const ray = new THREE.Raycaster(origin, forward, 0.01, 0.58)
        const found = ray.intersectObjects(targets, true)[0]
        if (!found) continue
        count += 1
        hits.push({
          radius,
          angle,
          distance: found.distance,
          point: found.point.clone(),
          name: found.object.name,
        })
      }
      byRadius.push({ radius, hits: count })
    })
    let holeRadius = 0.11
    const openRow = [...byRadius].reverse().find((row) => row.hits <= 5)
    const rimRow = byRadius.find((row) => row.hits >= 8)
    if (openRow && rimRow && rimRow.radius > openRow.radius) {
      holeRadius = (openRow.radius + rimRow.radius) / 2
    } else if (rimRow && byRadius.every((row) => row.hits >= 8)) {
      holeRadius = 0.11
    } else if (rimRow) {
      holeRadius = rimRow.radius
    }
    const rimHits = hits.filter((hit) => Math.abs(hit.radius - holeRadius) < 0.015)
    const used = rimHits.length >= 5 ? rimHits : hits
    const cavityCenter = new THREE.Vector3()
    used.forEach((hit) => cavityCenter.add(hit.point))
    if (used.length) cavityCenter.multiplyScalar(1 / used.length)
    else cavityCenter.copy(steerCenter).addScaledVector(forward, 0.22)
    const distances = used.map((hit) => hit.distance)
    lastCavityScan = {
      nodeNames: [...new Set(hits.map((hit) => hit.name))],
      holeRadius,
      hitCount: hits.length,
      rimHitCount: used.length,
      byRadius,
      steerCenter: steerCenter.toArray(),
      forward: forward.toArray(),
      cavityCenter: cavityCenter.toArray(),
      avgDistance: distances.length ? distances.reduce((sum, item) => sum + item, 0) / distances.length : 0.22,
      minDistance: distances.length ? Math.min(...distances) : null,
      maxDistance: distances.length ? Math.max(...distances) : null,
      localSize: [holeRadius * 2, holeRadius * 1.732, 0.03],
    }
    return { ...lastCavityScan, cavityCenter, forward, steerCenter }
  }

  const ensureDemoCluster = () => {
    if (demoCluster) return demoCluster
    const canvas2d = document.createElement('canvas')
    canvas2d.width = 512
    canvas2d.height = 512
    const ctx = canvas2d.getContext('2d')
    const texture = new THREE.CanvasTexture(canvas2d)
    texture.colorSpace = THREE.SRGBColorSpace
    const bezelMat = new THREE.MeshPhysicalMaterial({
      color: '#101214',
      roughness: 0.94,
      metalness: 0.03,
      envMapIntensity: 0.05,
      polygonOffset: true,
      polygonOffsetFactor: 1,
      polygonOffsetUnits: 1,
    })
    const plateMat = new THREE.MeshPhysicalMaterial({
      color: '#070809',
      roughness: 0.98,
      metalness: 0.01,
      envMapIntensity: 0.02,
      side: THREE.DoubleSide,
      polygonOffset: true,
      polygonOffsetFactor: 2,
      polygonOffsetUnits: 2,
    })
    const screenMat = new THREE.MeshPhysicalMaterial({
      map: texture,
      emissive: new THREE.Color('#4e6574'),
      emissiveMap: texture,
      emissiveIntensity: 0.18,
      roughness: 0.46,
      metalness: 0.03,
      transparent: true,
      opacity: 0.96,
      polygonOffset: true,
      polygonOffsetFactor: -1,
      polygonOffsetUnits: -1,
    })
    const group = new THREE.Group()
    group.name = 'CourseDemoInstrument'
    const plate = new THREE.Mesh(hexGeometry(0.132), plateMat)
    plate.name = 'CourseDemoBackplate'
    const bezel = new THREE.Mesh(hexGeometry(0.102), bezelMat)
    bezel.name = 'CourseDemoBezel'
    const screen = new THREE.Mesh(hexGeometry(0.068), screenMat)
    screen.name = 'CourseDemoCluster'
    plate.position.z = -0.016
    bezel.position.z = -0.006
    screen.position.z = 0.0015
    group.add(plate, bezel, screen)
    group.visible = false
    scene.add(group)
    demoCluster = {
      canvas: canvas2d,
      ctx,
      texture,
      material: screenMat,
      mesh: group,
      screen,
      bezel,
      plate,
      plateRadius: 0.132,
      bezelRadius: 0.102,
      screenRadius: 0.068,
    }
    drawDemoCluster()
    return demoCluster
  }

  const placeDemoCluster = () => {
    const cluster = ensureDemoCluster()
    const scan = scanInstrumentCavity()
    if (!scan) {
      cluster.mesh.visible = false
      return null
    }
    const hole = THREE.MathUtils.clamp(scan.holeRadius || 0.11, 0.08, 0.16)
    // 相对上一轮整体缩小约 6%，顶角更容易落在仪表台沿内。
    const plateR = THREE.MathUtils.clamp(hole * 1.22, 0.120, 0.162)
    const bezelR = THREE.MathUtils.clamp(hole * 0.90, 0.084, 0.108)
    const screenR = THREE.MathUtils.clamp(hole * 0.60, 0.052, 0.072)
    rebuildClusterGeometry(plateR, bezelR, screenR)
    // 空腔没有独立网格。用双侧座椅中心求 forward 会带上横向分量，
    // 把面板推到方向盘外侧。这里只沿车头方向缩进到方向盘后方的六边形孔。
    const ahead = new THREE.Vector3(0, 0, scan.forward.z >= 0 ? 1 : -1)
    const pos = scan.steerCenter.clone().addScaledVector(ahead, 0.194)
    pos.x = scan.steerCenter.x + 0.01
    pos.y = scan.steerCenter.y - 0.016
    const tiltX = -0.12
    cluster.mesh.position.copy(pos)
    cluster.mesh.lookAt(pos.clone().addScaledVector(ahead, -1))
    cluster.mesh.rotateX(tiltX)
    cluster.mesh.visible = interiorMode
    const alongForward = pos.clone().sub(scan.steerCenter).dot(scan.forward)
    cluster.tiltX = tiltX
    return {
      position: pos.toArray(),
      plateRadius: plateR,
      bezelRadius: bezelR,
      screenRadius: screenR,
      recessed: alongForward,
      behindSteering: alongForward > 0.08,
      tiltX,
      emissiveIntensity: cluster.material.emissiveIntensity,
      cavity: lastCavityScan,
    }
  }

  const setInteriorMode = (enabled) => {
    interiorMode = Boolean(enabled)
    cabinFill.intensity = interiorMode ? 0.48 : 0
    cabinDash.intensity = interiorMode ? 0.28 : 0
    if (demoCluster) demoCluster.mesh.visible = Boolean(enabled)
    if (interiorMode) controls.autoRotate = false
    ;(bindings?.Glass || []).forEach((material) => {
      if (!glassDefaults.has(material)) {
        glassDefaults.set(material, {
          transparent: material.transparent,
          opacity: material.opacity,
          depthWrite: material.depthWrite,
          roughness: material.roughness,
          metalness: material.metalness,
          envMapIntensity: material.envMapIntensity,
        })
      }
      const original = glassDefaults.get(material)
      if (interiorMode) {
        material.transparent = true
        material.opacity = 0.06
        material.depthWrite = false
        if ('roughness' in material) material.roughness = 0.06
        if ('metalness' in material) material.metalness = 0
        if ('envMapIntensity' in material) material.envMapIntensity = 0.08
      } else {
        Object.assign(material, original)
      }
      material.needsUpdate = true
    })
  }

  const placeInteriorCamera = () => {
    if (!interiorParts.meshes.length) collectInteriorParts()
    const box = getInteriorBox()
    const usable = box.isEmpty() ? new THREE.Box3().setFromObject(modelRoot) : box
    const size = usable.getSize(new THREE.Vector3())
    const center = usable.getCenter(new THREE.Vector3())
    const driverSeats = interiorParts.seats.filter((item) => /driver/i.test(item.name))
    const seatBox = boxFromMeshes(driverSeats.length ? driverSeats : interiorParts.seats)
    const steerBox = boxFromMeshes(interiorParts.steering)
    const dashBox = boxFromMeshes(interiorParts.dash)
    const position = new THREE.Vector3()
    const target = new THREE.Vector3()
    const forward = new THREE.Vector3(0, 0, 1)
    if (!steerBox.isEmpty() && !seatBox.isEmpty()) {
      const seatCenter = seatBox.getCenter(new THREE.Vector3())
      const steerCenter = steerBox.getCenter(new THREE.Vector3())
      forward.copy(steerCenter).sub(seatCenter)
      forward.y = 0
      if (forward.lengthSq() < 0.002) forward.set(0, 0, 1)
      else forward.normalize()
      const cabinCenter = usable.getCenter(new THREE.Vector3())
      const variant = MANIFEST.cameras[lastPreset]?.variant || 'driver'
      if (variant === 'cabinWide') {
        position.copy(cabinCenter)
        position.addScaledVector(forward, -0.48)
        position.y = usable.min.y + size.y * 0.74
        position.x = cabinCenter.x
        target.copy(steerCenter)
        target.x = cabinCenter.x * 0.62 + steerCenter.x * 0.38
        target.y = Math.max(steerCenter.y - 0.02, usable.min.y + size.y * 0.5)
        target.addScaledVector(forward, 0.06)
      } else {
        position.copy(steerCenter)
        position.addScaledVector(forward, -0.44)
        position.y = Math.max(steerCenter.y + 0.15, seatBox.min.y + seatBox.getSize(new THREE.Vector3()).y * 0.74)
        position.x = steerCenter.x * 0.72 + cabinCenter.x * 0.28
        target.copy(steerCenter)
        target.addScaledVector(forward, 0.34)
        target.y = steerCenter.y + 0.2
        target.x = steerCenter.x * 0.78 + cabinCenter.x * 0.22
        if (!dashBox.isEmpty()) {
          const dashCenter = dashBox.getCenter(new THREE.Vector3())
          target.y = Math.max(target.y, dashCenter.y + 0.05)
        }
      }
    } else {
      position.set(center.x - size.x * 0.18, usable.min.y + size.y * 0.58, center.z - size.z * 0.12)
      target.set(center.x - size.x * 0.04, usable.min.y + size.y * 0.5, usable.max.z - size.z * 0.18)
    }
    const pad = new THREE.Vector3(
      Math.min(0.07, size.x * 0.08),
      Math.min(0.05, size.y * 0.08),
      Math.min(0.07, size.z * 0.08),
    )
    const innerMin = usable.min.clone().add(pad)
    const innerMax = usable.max.clone().sub(pad)
    position.clamp(innerMin, innerMax)
    target.clamp(usable.min.clone().addScalar(0.02), usable.max.clone().subScalar(0.02))
    if (position.distanceTo(target) < 0.12) {
      target.copy(position).addScaledVector(forward, 0.28)
      target.clamp(usable.min.clone().addScalar(0.02), usable.max.clone().subScalar(0.02))
    }
    camera.position.copy(position)
    controls.target.copy(target)
    camera.lookAt(target)
    camera.updateMatrixWorld(true)
    applyInteriorControls(Math.max(size.x, size.z, size.y))
    const damping = controls.enableDamping
    controls.enableDamping = false
    controls.update()
    controls.enableDamping = damping
    cabinFill.position.copy(position).lerp(target, 0.28)
    cabinFill.position.y += 0.06
    cabinDash.position.copy(position).add(new THREE.Vector3(0, 0.12, 0))
    cabinDash.target.position.copy(target)
    cabinDash.target.updateMatrixWorld()
    placeDemoCluster()
    return { position, target, box: usable }
  }

  const cancelCameraAnim = () => {
    cameraAnimToken += 1
    animating = false
  }

  const frameModel = (presetId = lastPreset, animate = false) => {
    if (!modelRoot) return
    cancelCameraAnim()
    lastPreset = presetId || 'front45'
    const fromPos = camera.position.clone()
    const fromTarget = controls.target.clone()
    const fromFov = camera.fov
    const groundedSize = applyGrounding()
    fitLights(groundedSize)
    const preset = MANIFEST.cameras[lastPreset] || MANIFEST.cameras.front45
    setInteriorMode(Boolean(preset.interior))
    const target = preset.target
      ? new THREE.Vector3(...preset.target)
      : lookTarget.clone()
    if (preset.interior) {
      placeInteriorCamera()
    } else {
      controls.minPolarAngle = MANIFEST.framing.polarMin
      controls.maxPolarAngle = MANIFEST.framing.polarMax
      controls.minAzimuthAngle = -Infinity
      controls.maxAzimuthAngle = Infinity
      controls.minDistance = 0.08
      controls.maxDistance = 120
      camera.fov = EXTERIOR_FOV
      camera.near = 0.08
      camera.updateProjectionMatrix()
      const distance = distanceForView(preset.azimuth, preset.polar, target)
      placeCamera(preset.azimuth, preset.polar, distance, target)
      composeOnStage()
      nudgeToWidth()
      composeOnStage()
      nudgeToWidth()
      fitDistance = camera.position.distanceTo(controls.target)
      target.copy(controls.target)
      applyExteriorControls()
    }
    const nextPos = camera.position.clone()
    const nextTarget = controls.target.clone()
    const nextFov = camera.fov
    const goingInterior = Boolean(preset.interior)
    if (!animate || reduceMotion) {
      if (goingInterior) applyInteriorControls(Math.max(getInteriorBox().getSize(new THREE.Vector3()).x, 1))
      else applyExteriorControls()
      const damping = controls.enableDamping
      controls.enableDamping = false
      controls.update()
      controls.enableDamping = damping
      kick()
      return
    }
    camera.position.copy(fromPos)
    controls.target.copy(fromTarget)
    camera.fov = fromFov
    camera.updateProjectionMatrix()
    const token = cameraAnimToken
    const started = performance.now()
    animating = true
    const step = (now) => {
      if (disposed || token !== cameraAnimToken) return
      const t = Math.min(1, (now - started) / CAMERA_MS)
      const ease = 1 - (1 - t) ** 3
      camera.position.lerpVectors(fromPos, nextPos, ease)
      controls.target.lerpVectors(fromTarget, nextTarget, ease)
      camera.fov = fromFov + (nextFov - fromFov) * ease
      camera.updateProjectionMatrix()
      camera.lookAt(controls.target)
      kick()
      if (t < 1) {
        window.requestAnimationFrame(step)
        return
      }
      camera.position.copy(nextPos)
      controls.target.copy(nextTarget)
      camera.fov = nextFov
      camera.updateProjectionMatrix()
      camera.lookAt(controls.target)
      if (goingInterior) applyInteriorControls(Math.max(getInteriorBox().getSize(new THREE.Vector3()).length(), 1))
      else applyExteriorControls()
      const damping = controls.enableDamping
      controls.enableDamping = false
      controls.update()
      controls.enableDamping = damping
      animating = false
      kick()
    }
    window.requestAnimationFrame(step)
  }

  const resize = () => {
    if (disposed) return
    const width = canvas.clientWidth || canvas.parentElement?.clientWidth || 1
    const height = canvas.clientHeight || canvas.parentElement?.clientHeight || 1
    renderer.setSize(width, height, false)
    camera.aspect = width / Math.max(1, height)
    camera.updateProjectionMatrix()
    if (modelRoot && !isInteriorPreset(lastPreset)) frameModel(lastPreset, false)
    kick()
  }

  const finishOf = (code) => (
    MANIFEST.paintFinish[code] || MANIFEST.paintFinish.WHITE
  )

  const auditTextureColorSpace = (material) => {
    if (!material) return
    if (material.map?.isTexture) material.map.colorSpace = THREE.SRGBColorSpace
    if (material.emissiveMap?.isTexture) material.emissiveMap.colorSpace = THREE.SRGBColorSpace
    if (material.specularColorMap?.isTexture) material.specularColorMap.colorSpace = THREE.SRGBColorSpace
    ;['normalMap', 'roughnessMap', 'metalnessMap', 'aoMap', 'bumpMap', 'displacementMap'].forEach((key) => {
      if (material[key]?.isTexture) material[key].colorSpace = THREE.NoColorSpace
    })
  }

  const fitLights = (size) => {
    if (!size) return
    const reach = Math.max(size.x, size.z, 2.4)
    key.position.set(reach * 1.08, size.y * 3.2 + 2.1, reach * 0.68)
    fill.position.set(-reach * 1.12, size.y * 1.05 + 1.0, reach * 0.98)
    rim.position.set(-reach * 0.32, size.y * 2.7 + 1.5, -reach * 1.38)
    bounce.position.set(reach * 0.14, Math.max(0.42, size.y * 0.22), reach * 0.52)
    key.shadow.camera.left = -reach * 1.02
    key.shadow.camera.right = reach * 1.02
    key.shadow.camera.top = reach * 0.7
    key.shadow.camera.bottom = -reach * 0.52
    key.shadow.camera.near = 0.8
    key.shadow.camera.far = reach * 4.1 + 8
    key.shadow.camera.updateProjectionMatrix()
  }

  const tunePaintMaterial = (material, extras) => {
    Object.assign(material, extras)
    if (material.map) material.map = null
    auditTextureColorSpace(material)
    material.needsUpdate = true
  }

  const upgradePaintMaterials = () => {
    if (!modelRoot) return
    const replacements = new Map()
    const white = finishOf('WHITE')
    modelRoot.traverse((object) => {
      if (!object.isMesh) return
      object.castShadow = true
      object.receiveShadow = true
      const list = Array.isArray(object.material) ? object.material : [object.material]
      list.forEach((material) => auditTextureColorSpace(material))
      const next = list.map((material) => {
        if (!material || !bindings.BodyPaint.includes(material)) return material
        if (replacements.has(material)) return replacements.get(material)
        const paint = new THREE.MeshPhysicalMaterial({
          color: material.color?.clone() || new THREE.Color('#ebe6dc'),
          map: null,
          normalMap: material.normalMap || null,
          normalScale: material.normalScale?.clone?.() || new THREE.Vector2(1, 1),
          roughnessMap: material.roughnessMap || null,
          metalnessMap: null,
          metalness: white.metalness,
          roughness: white.roughness,
          clearcoat: white.clearcoat,
          clearcoatRoughness: white.clearcoatRoughness,
          envMap,
          envMapIntensity: white.envMapIntensity,
          ior: 1.5,
          specularIntensity: white.specularIntensity,
        })
        paint.name = material.name
        replacements.set(material, paint)
        return paint
      })
      object.material = Array.isArray(object.material) ? next : next[0]
    })
    if (replacements.size) {
      bindings.BodyPaint = [...replacements.values()]
    }
  }

  const loadEnvironment = async () => {
    const { RGBELoader } = await import('three/examples/jsm/loaders/RGBELoader.js')
    const pmrem = new THREE.PMREMGenerator(renderer)
    try {
      const hdr = await new RGBELoader().loadAsync(MANIFEST.environmentUrl)
      hdr.mapping = THREE.EquirectangularReflectionMapping
      envMap = pmrem.fromEquirectangular(hdr).texture
      hdr.dispose()
    } catch {
      envMap = null
    }
    scene.environment = envMap
    scene.environmentIntensity = 0.72
    pmrem.dispose()
    onProgress({ stage: 'environment', percent: 28 })
  }

  const loadModel = async () => {
    const manager = new THREE.LoadingManager()
    manager.onProgress = (_url, loaded, total) => {
      onProgress({ stage: 'model', percent: 28 + Math.round((loaded / Math.max(1, total)) * 70) })
    }
    const loader = new GLTFLoader(manager)
    const draco = new DRACOLoader()
    draco.setDecoderPath(MANIFEST.decoder.draco)
    loader.setDRACOLoader(draco)
    const ktx2 = new KTX2Loader()
    ktx2.setTranscoderPath(MANIFEST.decoder.basis)
    ktx2.detectSupport(renderer)
    loader.setKTX2Loader(ktx2)
    try {
      if (MeshoptDecoder?.ready) await MeshoptDecoder.ready
      if (MeshoptDecoder) loader.setMeshoptDecoder(MeshoptDecoder)
    } catch {
      /* 当前 GLB 未使用 meshopt */
    }

    const gltf = await loader.loadAsync(MANIFEST.modelUrl)
    modelRoot = gltf.scene
    modelRoot.name = 'CourseDemoCar'
    const collected = collectRoleBindings(modelRoot)
    bindings = collected.bindings
    wheelRoots = collected.wheelRoots
    scene.add(modelRoot)
    collectInteriorParts()
    modelRoot.traverse((object) => {
      const name = `${object.name || ''} ${object.parent?.name || ''}`
      if (/steeringwheelbadge/i.test(name)) object.visible = false
    })
    upgradePaintMaterials()
    frameModel('front45', false)
    draco.dispose()
    ktx2.dispose()
    onProgress({ stage: 'ready', percent: 100 })
  }

  const lerpMaterials = (materials, hex, extra = {}) => {
    if (!materials?.length) return Promise.resolve()
    window.cancelAnimationFrame(colorTimer)
    const token = ++colorToken
    const target = new THREE.Color(hex)
    const starts = materials.map((material) => material.color.clone())
    const apply = (amount) => {
      materials.forEach((material, index) => {
        material.color.copy(starts[index]).lerp(target, amount)
        if (amount >= 1) tunePaintMaterial(material, extra)
      })
      kick()
    }
    if (reduceMotion) {
      apply(1)
      return Promise.resolve()
    }
    const started = performance.now()
    animating = true
    return new Promise((resolve) => {
      const step = (now) => {
        if (disposed || token !== colorToken) {
          animating = false
          resolve()
          return
        }
        const t = Math.min(1, (now - started) / COLOR_MS)
        apply(t)
        if (t < 1) {
          colorTimer = window.requestAnimationFrame(step)
          return
        }
        animating = false
        resolve()
      }
      colorTimer = window.requestAnimationFrame(step)
    })
  }

  const setBodyColor = (code) => {
    const hex = MANIFEST.paints[code] || MANIFEST.paints.WHITE
    return lerpMaterials(bindings?.BodyPaint || [], hex, finishOf(code))
  }

  const setWheel = async (code) => {
    const spec = MANIFEST.wheels[code] || MANIFEST.wheels.W19
    wheelRoots.forEach((node) => {
      node.visible = true
      node.scale.setScalar(spec.scale)
    })
    await lerpMaterials(bindings?.Wheel || [], spec.rim, {
      metalness: spec.metalness,
      roughness: spec.roughness,
      envMapIntensity: 0.35,
    })
    if (!isInteriorPreset(lastPreset)) frameModel(lastPreset, false)
  }

  const shadeHex = (hex, factor) => {
    const color = new THREE.Color(hex)
    color.multiplyScalar(factor)
    return `#${color.getHexString()}`
  }

  const setInterior = (code) => {
    const hex = MANIFEST.interiors[code] || MANIFEST.interiors.BLACK
    const jobs = [
      lerpMaterials([...(bindings?.Interior || []), ...(bindings?.Seat || [])], hex, {
        metalness: 0.12,
        roughness: 0.58,
        envMapIntensity: 0.28,
      }),
      lerpMaterials(bindings?.Seatbelt || [], shadeHex(hex, 0.72), {
        metalness: 0.08,
        roughness: 0.62,
        envMapIntensity: 0.16,
      }),
      lerpMaterials(bindings?.InteriorAccent || [], shadeHex(hex, 0.88), {
        metalness: 0.14,
        roughness: 0.5,
        envMapIntensity: 0.2,
      }),
    ]
    return Promise.all(jobs)
  }

  const setPacks = (codes = []) => {
    const track = codes.includes('TRACK')
    const hex = track ? MANIFEST.packs.TRACK.caliper : DEFAULT_CALIPER
    lerpMaterials(bindings?.Caliper || [], hex, { metalness: 0.4, roughness: 0.42, envMapIntensity: 0.22 })
    return codes
      .filter((code) => MANIFEST.packs[code] && MANIFEST.packs[code].preview === false)
      .map((code) => MANIFEST.packs[code].reason)
  }

  const setCameraPreset = (id, immediate = false) => {
    frameModel(id || 'front45', !immediate && !reduceMotion)
  }

  const resetCamera = () => setCameraPreset('front45', true)

  const setAutoRotate = (enabled) => {
    controls.autoRotate = Boolean(enabled)
    controls.autoRotateSpeed = 0.55
    if (enabled) kick()
    else kick()
  }

  const getVehicleScreenBounds = () => {
    if (!modelRoot) return null
    const box = new THREE.Box3().setFromObject(modelRoot)
    const width = canvas.clientWidth || 1
    const height = canvas.clientHeight || 1
    const point = new THREE.Vector3()
    let minX = 1
    let maxX = 0
    let minY = 1
    let maxY = 0
    boxCorners(box, THREE).forEach((corner) => {
      point.copy(corner).project(camera)
      const x = (point.x * 0.5 + 0.5)
      const y = (-point.y * 0.5 + 0.5)
      minX = Math.min(minX, x)
      maxX = Math.max(maxX, x)
      minY = Math.min(minY, y)
      maxY = Math.max(maxY, y)
    })
    return {
      left: minX,
      right: maxX,
      top: minY,
      bottom: maxY,
      width: maxX - minX,
      height: maxY - minY,
      center: (minY + maxY) / 2,
      topEmpty: minY,
      canvasWidth: width,
      canvasHeight: height,
    }
  }

  const sampleInstrumentPixels = () => {
    if (!demoCluster?.mesh.visible || disposed) return null
    renderOnce()
    const width = renderer.domElement.width
    const height = renderer.domElement.height
    const gl = renderer.getContext()
    if (!width || !height || !gl) return null
    const buffer = new Uint8Array(width * height * 4)
    gl.readPixels(0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, buffer)
    const project = (vec) => {
      const point = vec.clone().project(camera)
      return {
        x: Math.round((point.x * 0.5 + 0.5) * (width - 1)),
        y: Math.round((point.y * 0.5 + 0.5) * (height - 1)),
      }
    }
    const origin = demoCluster.mesh.getWorldPosition(new THREE.Vector3())
    const quat = demoCluster.mesh.getWorldQuaternion(new THREE.Quaternion())
    const local = (x, y, z) => new THREE.Vector3(x, y, z).applyQuaternion(quat).add(origin)
    const center = project(origin)
    const rim = project(local(demoCluster.bezelRadius, 0, 0))
    const screen = project(local(demoCluster.screenRadius, 0, 0))
    const rimPx = Math.max(6, Math.hypot(rim.x - center.x, rim.y - center.y))
    const screenPx = Math.max(4, Math.hypot(screen.x - center.x, screen.y - center.y))
    const read = (x, y) => {
      if (x < 0 || y < 0 || x >= width || y >= height) return null
      const i = ((y * width) + x) * 4
      return [buffer[i], buffer[i + 1], buffer[i + 2]]
    }
    const isSky = (rgb) => rgb && rgb[2] > 140 && rgb[1] > 120 && rgb[0] > 90 && rgb[2] > rgb[0] + 12 && rgb[0] + rgb[1] + rgb[2] > 380
    const lumaOf = (rgb) => (rgb ? 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2] : 0)
    let halo = 0
    let haloSky = 0
    let screenSamples = 0
    let screenNearWhite = 0
    let screenMax = 0
    for (let i = 0; i < 72; i += 1) {
      const angle = (i / 72) * Math.PI * 2
      for (const scale of [1.08, 1.2, 1.34]) {
        const x = Math.round(center.x + Math.cos(angle) * rimPx * scale)
        const y = Math.round(center.y + Math.sin(angle) * rimPx * scale)
        const rgb = read(x, y)
        if (!rgb) continue
        halo += 1
        if (isSky(rgb)) haloSky += 1
      }
      const sx = Math.round(center.x + Math.cos(angle) * screenPx * 0.45)
      const sy = Math.round(center.y + Math.sin(angle) * screenPx * 0.45)
      const srgb = read(sx, sy)
      if (!srgb) continue
      screenSamples += 1
      const luma = lumaOf(srgb)
      if (luma > screenMax) screenMax = luma
      if (srgb[0] >= 250 && srgb[1] >= 250 && srgb[2] >= 250) screenNearWhite += 1
    }
    const mid = read(center.x, center.y)
    if (mid) {
      screenSamples += 1
      const luma = lumaOf(mid)
      if (luma > screenMax) screenMax = luma
      if (mid[0] >= 250 && mid[1] >= 250 && mid[2] >= 250) screenNearWhite += 1
    }
    return {
      skyHaloRatio: haloSky / Math.max(1, halo),
      screenNearWhiteRatio: screenNearWhite / Math.max(1, screenSamples),
      screenMaxLuma: screenMax,
      halo,
      screenSamples,
      rimPx,
      screenPx,
    }
  }

  const frameInstrumentCloseup = () => {
    if (!demoCluster) placeDemoCluster()
    if (!demoCluster) return null
    setInteriorMode(true)
    placeDemoCluster()
    const pos = demoCluster.mesh.getWorldPosition(new THREE.Vector3())
    const towardDriver = new THREE.Vector3()
    demoCluster.mesh.getWorldDirection(towardDriver)
    camera.position.copy(pos).addScaledVector(towardDriver, 0.36)
    camera.fov = 40
    camera.near = 0.04
    camera.updateProjectionMatrix()
    controls.target.copy(pos)
    camera.lookAt(pos)
    camera.updateMatrixWorld(true)
    kick()
    return {
      position: camera.position.toArray(),
      target: pos.toArray(),
      fov: camera.fov,
    }
  }

  const meshVisibleInView = (mesh) => {
    if (!mesh?.geometry) return false
    camera.updateMatrixWorld(true)
    camera.updateProjectionMatrix()
    const frustum = new THREE.Frustum()
    frustum.setFromProjectionMatrix(
      new THREE.Matrix4().multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse),
    )
    if (!mesh.geometry.boundingSphere) mesh.geometry.computeBoundingSphere()
    return frustum.intersectsObject(mesh)
  }

  const getInteriorDebug = () => {
    if (!modelRoot) return null
    if (!interiorParts.meshes.length) collectInteriorParts()
    const interiorBox = getInteriorBox()
    const pack = (box) => (box.isEmpty() ? null : ({
      min: box.min.toArray(),
      max: box.max.toArray(),
      center: box.getCenter(new THREE.Vector3()).toArray(),
      size: box.getSize(new THREE.Vector3()).toArray(),
    }))
    const cameraInside = !interiorBox.isEmpty() && interiorBox.containsPoint(camera.position)
    const padded = interiorBox.clone().expandByScalar(0.12)
    const cameraNearCabin = !interiorBox.isEmpty() && padded.containsPoint(camera.position)
    const visible = {
      steering: interiorParts.steering.filter((item) => meshVisibleInView(item)).map((item) => item.name),
      dashboard: interiorParts.dash.filter((item) => meshVisibleInView(item)).map((item) => item.name),
      seat: interiorParts.seats.filter((item) => meshVisibleInView(item)).map((item) => item.name),
    }
    const glass = (bindings?.Glass || []).map((material) => ({
      name: material.name,
      opacity: material.opacity,
      transparent: material.transparent,
    }))
    const steeringRatio = screenAreaRatio(interiorParts.steering)
    const { steerCenter, forward } = cabinAxes()
    const alongForward = demoCluster
      ? demoCluster.mesh.position.clone().sub(steerCenter).dot(forward)
      : 0
    const cluster = demoCluster ? {
      visible: demoCluster.mesh.visible,
      position: demoCluster.mesh.position.toArray(),
      plateRadius: demoCluster.plateRadius,
      bezelRadius: demoCluster.bezelRadius,
      screenRadius: demoCluster.screenRadius,
      emissiveIntensity: demoCluster.material.emissiveIntensity,
      name: demoCluster.mesh.name,
      behindSteering: alongForward > 0.08,
      recessed: alongForward,
      tiltX: demoCluster.tiltX ?? -0.12,
      cavity: lastCavityScan,
    } : null
    const badge = []
    modelRoot.traverse((object) => {
      const name = `${object.name || ''} ${object.parent?.name || ''}`
      if (/steeringwheelbadge/i.test(name)) {
        const mats = Array.isArray(object.material) ? object.material : [object.material]
        badge.push({
          name: object.name,
          parent: object.parent?.name || '',
          visible: object.visible,
          materials: mats.map((item) => item?.name).filter(Boolean),
        })
      }
    })
    return {
      interiorMode,
      interiorBox: pack(interiorBox),
      camera: camera.position.toArray(),
      target: controls.target.toArray(),
      cameraInside,
      cameraNearCabin,
      fov: camera.fov,
      near: camera.near,
      minDistance: controls.minDistance,
      maxDistance: controls.maxDistance,
      minPolar: controls.minPolarAngle,
      maxPolar: controls.maxPolarAngle,
      minAzimuth: controls.minAzimuthAngle,
      maxAzimuth: controls.maxAzimuthAngle,
      cabinFill: cabinFill.intensity,
      cabinDash: cabinDash.intensity,
      toneMappingExposure: renderer.toneMappingExposure,
      meshes: interiorParts.meshes.map((item) => item.name),
      visible,
      visibleCount: {
        steering: visible.steering.length,
        dashboard: visible.dashboard.length,
        seat: visible.seat.length,
      },
      steeringScreenRatio: steeringRatio,
      cluster,
      instrumentPixels: sampleInstrumentPixels(),
      bindings: {
        interior: (bindings?.Interior || []).map((item) => item.name),
        seat: (bindings?.Seat || []).map((item) => item.name),
        seatbelt: (bindings?.Seatbelt || []).map((item) => item.name),
        accent: (bindings?.InteriorAccent || []).map((item) => item.name),
      },
      glass,
      badge,
    }
  }

  const screenAreaRatio = (meshes) => {
    if (!meshes?.length) return 0
    const point = new THREE.Vector3()
    let minX = 1
    let maxX = 0
    let minY = 1
    let maxY = 0
    let hits = 0
    meshes.forEach((mesh) => {
      const box = new THREE.Box3().setFromObject(mesh)
      boxCorners(box, THREE).forEach((corner) => {
        point.copy(corner).project(camera)
        const x = point.x * 0.5 + 0.5
        const y = -point.y * 0.5 + 0.5
        if (x < -0.2 || x > 1.2 || y < -0.2 || y > 1.2) return
        hits += 1
        minX = Math.min(minX, x)
        maxX = Math.max(maxX, x)
        minY = Math.min(minY, y)
        maxY = Math.max(maxY, y)
      })
    })
    if (!hits) return 0
    return Math.max(0, Math.min(1, (maxX - minX) * (maxY - minY)))
  }

  const getRestoreSnapshot = () => {
    const bounds = getVehicleScreenBounds()
    return {
      interiorMode,
      fov: camera.fov,
      near: camera.near,
      position: camera.position.toArray(),
      target: controls.target.toArray(),
      minDistance: controls.minDistance,
      maxDistance: controls.maxDistance,
      minPolar: controls.minPolarAngle,
      maxPolar: controls.maxPolarAngle,
      minAzimuth: controls.minAzimuthAngle,
      maxAzimuth: controls.maxAzimuthAngle,
      cabinFill: cabinFill.intensity,
      cabinDash: cabinDash.intensity,
      bounds,
    }
  }

  const getSceneDebug = () => {
    if (!modelRoot) return null
    const modelBox = new THREE.Box3().setFromObject(modelRoot)
    const pack = (box) => (box.isEmpty() ? null : ({
      min: box.min.toArray(),
      max: box.max.toArray(),
      center: box.getCenter(new THREE.Vector3()).toArray(),
      size: box.getSize(new THREE.Vector3()).toArray(),
    }))
    return {
      model: pack(modelBox),
      interior: pack(getInteriorBox()),
      camera: camera.position.toArray(),
      target: controls.target.toArray(),
      interiorMode,
    }
  }

  canvas.addEventListener('webglcontextlost', onContextLost)
  canvas.addEventListener('webglcontextrestored', kick)
  canvas.addEventListener('pointerdown', onPointerDown)
  canvas.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
  canvas.addEventListener('wheel', kick, { passive: true })
  const onControlsChange = () => {
    if (interiorMode) {
      const box = getInteriorBox()
      if (!box.isEmpty()) {
        const pad = box.clone().expandByScalar(-0.04)
        if (!pad.isEmpty()) controls.target.clamp(pad.min, pad.max)
      }
    }
    kick()
  }
  controls.addEventListener('change', onControlsChange)
  document.addEventListener('visibilitychange', onVisibility)
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas.parentElement || canvas)
  if (typeof IntersectionObserver === 'function') {
    intersectObserver = new IntersectionObserver((entries) => {
      inView = entries.some((entry) => entry.isIntersecting)
      if (inView) kick()
      else stopLoop()
    }, { threshold: 0.05 })
    intersectObserver.observe(canvas)
  }
  resize()

  await loadEnvironment()
  await loadModel()
  kick()

  const dispose = () => {
    if (disposed) return
    disposed = true
    stopLoop()
    window.cancelAnimationFrame(colorTimer)
    document.removeEventListener('visibilitychange', onVisibility)
    window.removeEventListener('pointerup', onPointerUp)
    canvas.removeEventListener('webglcontextlost', onContextLost)
    canvas.removeEventListener('webglcontextrestored', kick)
    canvas.removeEventListener('pointerdown', onPointerDown)
    canvas.removeEventListener('pointermove', onPointerMove)
    canvas.removeEventListener('wheel', kick)
    controls.removeEventListener('change', onControlsChange)
    resizeObserver?.disconnect()
    intersectObserver?.disconnect()
    controls.dispose()
    if (modelRoot) {
      scene.remove(modelRoot)
      disposeObject(modelRoot)
    }
    scene.remove(cabinFill)
    scene.remove(cabinDash)
    scene.remove(cabinDash.target)
    cabinFill.dispose?.()
    cabinDash.dispose?.()
    if (demoCluster) {
      scene.remove(demoCluster.mesh)
      demoCluster.bezel.geometry.dispose()
      demoCluster.plate.geometry.dispose()
      demoCluster.screen.geometry.dispose()
      demoCluster.bezel.material.dispose()
      demoCluster.plate.material.dispose()
      demoCluster.material.dispose()
      demoCluster.texture.dispose()
      demoCluster = null
    }
    disposeObject(floor)
    disposeObject(contact)
    backdropMap.dispose()
    envMap?.dispose()
    renderer.dispose()
    renderer.forceContextLoss?.()
  }

  const captureAngleThumbs = async () => {
    if (!modelRoot || disposed) return {}
    const ids = ['front45', 'side', 'rear45', 'rear', 'front']
    const savedPos = camera.position.clone()
    const savedTarget = controls.target.clone()
    const savedAspect = camera.aspect
    const savedMin = controls.minDistance
    const savedMax = controls.maxDistance
    const off = document.createElement('canvas')
    off.width = 320
    off.height = 180
    off.setAttribute('aria-hidden', 'true')
    Object.assign(off.style, {
      position: 'fixed',
      left: '-9999px',
      top: '0',
      width: '320px',
      height: '180px',
      opacity: '0',
      pointerEvents: 'none',
    })
    document.body.appendChild(off)
    let offRenderer = null
    const shots = {}
    try {
      unlockDistance()
      offRenderer = new THREE.WebGLRenderer({
        canvas: off,
        antialias: true,
        alpha: false,
        preserveDrawingBuffer: true,
        powerPreference: 'low-power',
      })
      offRenderer.setSize(320, 180, false)
      offRenderer.outputColorSpace = THREE.SRGBColorSpace
      offRenderer.toneMapping = THREE.ACESFilmicToneMapping
      offRenderer.toneMappingExposure = renderer.toneMappingExposure
      offRenderer.shadowMap.enabled = false
      const thumbCam = camera.clone()
      thumbCam.aspect = 320 / 180
      thumbCam.updateProjectionMatrix()
      applyGrounding()
      ids.forEach((id) => {
        const preset = MANIFEST.cameras[id] || MANIFEST.cameras.front45
        const target = lookTarget.clone()
        const distance = distanceForView(preset.azimuth, preset.polar, target) * 1.06
        placeCamera(preset.azimuth, preset.polar, distance, target)
        thumbCam.position.copy(camera.position)
        thumbCam.lookAt(controls.target)
        thumbCam.updateMatrixWorld(true)
        offRenderer.render(scene, thumbCam)
        shots[id] = off.toDataURL('image/jpeg', 0.84)
      })
    } catch {
      return {}
    } finally {
      camera.position.copy(savedPos)
      controls.target.copy(savedTarget)
      camera.aspect = savedAspect
      camera.lookAt(controls.target)
      camera.updateProjectionMatrix()
      if (interiorMode) applyInteriorControls()
      else applyExteriorControls()
      controls.update()
      offRenderer?.dispose()
      offRenderer?.forceContextLoss?.()
      off.remove()
      kick()
    }
    return shots
  }

  const sampleExposure = () => {
    if (!modelRoot || disposed) return null
    renderOnce()
    const width = renderer.domElement.width
    const height = renderer.domElement.height
    const gl = renderer.getContext()
    if (!width || !height || !gl) return null
    const color = new Uint8Array(width * height * 4)
    gl.readPixels(0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, color)

    const paintSet = new Set(bindings?.BodyPaint || [])
    const saved = []
    const white = new THREE.MeshBasicMaterial({ color: 0xffffff })
    const black = new THREE.MeshBasicMaterial({ color: 0x000000 })
    const savedBg = scene.background
    const savedEnv = scene.environment
    scene.background = new THREE.Color(0x000000)
    scene.environment = null
    const maskItems = [hemi, key, fill, rim, bounce, cabinFill, floor, contact]
    const maskVisibility = maskItems.map((item) => item?.visible)
    maskItems.forEach((item) => {
      if (item) item.visible = false
    })
    modelRoot.traverse((object) => {
      if (!object.isMesh) return
      saved.push([object, object.material])
      const mats = Array.isArray(object.material) ? object.material : [object.material]
      object.material = mats.some((item) => paintSet.has(item)) ? white : black
    })
    renderer.render(scene, camera)
    const mask = new Uint8Array(width * height * 4)
    gl.readPixels(0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, mask)
    saved.forEach(([object, material]) => {
      object.material = material
    })
    white.dispose()
    black.dispose()
    scene.background = savedBg
    scene.environment = savedEnv
    maskItems.forEach((item, index) => {
      if (item) item.visible = maskVisibility[index]
    })
    kick()

    let samples = 0
    let nearWhite = 0
    let nearBlack = 0
    let transparent = 0
    let highlights = 0
    let sum = 0
    let minLuma = 255
    let maxLuma = 0
    for (let i = 0; i < mask.length; i += 4) {
      if (mask[i] < 200) continue
      const r = color[i]
      const g = color[i + 1]
      const b = color[i + 2]
      const a = color[i + 3]
      samples += 1
      if (a < 8) transparent += 1
      const luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
      sum += luma
      if (luma < minLuma) minLuma = luma
      if (luma > maxLuma) maxLuma = luma
      if (r >= 250 && g >= 250 && b >= 250) nearWhite += 1
      if (r <= 8 && g <= 8 && b <= 8) nearBlack += 1
      if (luma >= 150) highlights += 1
    }
    return {
      samples,
      nearWhiteRatio: nearWhite / Math.max(1, samples),
      nearBlackRatio: nearBlack / Math.max(1, samples),
      highlightRatio: highlights / Math.max(1, samples),
      transparentRatio: transparent / Math.max(1, samples),
      avgLuma: sum / Math.max(1, samples),
      minLuma: samples ? minLuma : 0,
      maxLuma: samples ? maxLuma : 0,
      canvasWidth: width,
      canvasHeight: height,
    }
  }

  return {
    setBodyColor,
    setWheel,
    setInterior,
    setPacks,
    setCameraPreset,
    isInteriorView: () => interiorMode,
    sampleExposure,
    setAutoRotate,
    resetCamera,
    frameModel,
    captureAngleThumbs,
    getVehicleScreenBounds,
    getSceneDebug,
    getInteriorDebug,
    getRestoreSnapshot,
    frameInstrumentCloseup,
    scanInstrumentCavity,
    setBadgeVisible: (visible) => {
      const found = []
      modelRoot?.traverse((object) => {
        const name = `${object.name || ''} ${object.parent?.name || ''}`
        if (!/steeringwheelbadge/i.test(name)) return
        object.visible = Boolean(visible)
        const mats = Array.isArray(object.material) ? object.material : [object.material]
        found.push({
          name: object.name,
          parent: object.parent?.name || '',
          visible: object.visible,
          materials: mats.map((item) => item?.name).filter(Boolean),
        })
      })
      kick()
      return found
    },
    isLoopRunning: () => loopRunning,
    getRenderCount: () => renderCount,
    resize,
    dispose,
    hasRole: (role) => Boolean(bindings?.[role]?.length || (role === 'Wheel' && wheelRoots.length)),
  }
}

export { detectWebGL }
