const ROLE_RULES = {
  // Caliper rules must be checked before BodyPaint: some third-party models
  // use names such as "CaliperAZonePaint" for brake materials.
  Caliper: { materials: [/cal+iper/i, /^brake$/i], nodes: [/cal+iper/i] },
  BodyPaint: { materials: [/^paint(?:$|[_ .-])/i] },
  Glass: { materials: [/glass/i, /window/i] },
  Wheel: { materials: [/^rim/i, /^wheel/i], nodes: [/wheel/i] },
  Tire: { materials: [/tire/i] },
  Interior: { materials: [/interior/i, /floormat/i, /dashboard/i], nodes: [/^interior/i] },
  Light: { materials: [/light/i] },
}

const INTERIOR_SUBTREE = /interior|seat|steering|dashboard|cockpit|cabin|shifter|pedal|belt/i
const SEATBELT_NODE = /belt/i
const SEAT_NODE = /seat/i
const ACCENT_MAT = /coloured|colored/i
const ACCENT_BLOCK = /paint|caliper|light|glass|wheel|badge|window/i

const isInteriorSubtree = (object) => {
  let current = object
  while (current) {
    if (INTERIOR_SUBTREE.test(current.name || '')) return true
    current = current.parent
  }
  return false
}

const matches = (name, patterns = []) => (
  patterns.some((pattern) => pattern.test(String(name || '')))
)

export const classifyMaterial = (name) => {
  const entry = Object.entries(ROLE_RULES).find(([, rule]) => matches(name, rule.materials))
  return entry ? entry[0] : null
}

export const classifyNode = (name) => {
  const entry = Object.entries(ROLE_RULES).find(([, rule]) => matches(name, rule.nodes))
  return entry ? entry[0] : null
}

export const collectRoleBindings = (root) => {
  const bindings = {
    BodyPaint: [],
    Glass: [],
    Wheel: [],
    Tire: [],
    Interior: [],
    Seat: [],
    Seatbelt: [],
    InteriorAccent: [],
    Caliper: [],
    Light: [],
  }
  const wheelRoots = []
  const seenMaterials = new WeakSet()

  root.traverse((object) => {
    const role = classifyNode(object.name)
    if (role === 'Wheel' && object.children?.length) wheelRoots.push(object)
    if (!object.isMesh) return
    const materials = Array.isArray(object.material) ? object.material : [object.material]
    materials.forEach((material) => {
      if (!material || seenMaterials.has(material)) return
      seenMaterials.add(material)
      const materialRole = classifyMaterial(material.name)
      if (materialRole) bindings[materialRole].push(material)
      if (!isInteriorSubtree(object)) return
      const nodeName = `${object.name || ''} ${object.parent?.name || ''}`
      if (SEATBELT_NODE.test(nodeName) || (ACCENT_MAT.test(material.name || '') && SEAT_NODE.test(nodeName))) {
        bindings.Seatbelt.push(material)
      } else if (SEAT_NODE.test(nodeName) && /interior/i.test(material.name || '')) {
        bindings.Seat.push(material)
      } else if (ACCENT_MAT.test(material.name || '') && !ACCENT_BLOCK.test(material.name || '')) {
        bindings.InteriorAccent.push(material)
      }
    })
  })

  return { bindings, wheelRoots }
}
