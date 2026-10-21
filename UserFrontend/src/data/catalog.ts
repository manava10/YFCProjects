export const materials = [
  {
    id: "20mm",
    name: "20 MM Aggregate",
    rate: 820,
    grade: "20 mm",
    use: "RCC, foundations, and road base that needs a larger lock.",
    image: "/media/agg-20.jpg",
  },
  {
    id: "10mm",
    name: "10 MM Aggregate",
    rate: 850,
    grade: "10 mm",
    use: "Slabs, precast, and mixes that call for a tighter grade.",
    image: "/media/agg-10.jpg",
  },
  {
    id: "vsi",
    name: "VSI Aggregate",
    rate: 780,
    grade: "Cubical",
    use: "Vertical-shaft impact stone. More cubical, cleaner interlock.",
    image: "/media/agg-vsi.jpg",
  },
  {
    id: "dust",
    name: "Stone Dust",
    rate: 650,
    grade: "Fines",
    use: "Filling, blending, and levelling under slabs and pavers.",
    image: "/media/agg-dust.jpg",
  },
  {
    id: "msand",
    name: "M Sand",
    rate: 950,
    grade: "Manufactured sand",
    use: "Plaster and mortar where a graded manufactured sand is specified.",
    image: "/media/agg-msand.jpg",
  },
] as const;

export type MaterialId = (typeof materials)[number]["id"];

export const plants = [
  {
    id: "kotputli",
    name: "Kotputli Crusher Plant",
    place: "Kotputli, Rajasthan",
    note: "Primary crush and screen. All five grades load from this yard.",
    image: "/media/plant.jpg",
  },
  {
    id: "behror",
    name: "Behror Crusher Plant",
    place: "Behror, Rajasthan",
    note: "Screening and fast loading on the NH48 run toward NCR.",
    image: "/media/hero.jpg",
  },
  {
    id: "bhiwadi",
    name: "Bhiwadi Dispatch Hub",
    place: "Bhiwadi, Rajasthan",
    note: "Staging yard for Gurugram, Manesar, and south Delhi drops.",
    image: "/media/truck.jpg",
  },
] as const;

export type PlantId = (typeof plants)[number]["id"];

export const destinations = [
  "Gurugram, Haryana",
  "Manesar, Haryana",
  "Faridabad, Haryana",
  "Delhi",
  "Noida, Uttar Pradesh",
  "Bhiwadi, Rajasthan",
  "Neemrana, Rajasthan",
  "Alwar, Rajasthan",
  "Jaipur, Rajasthan",
] as const;

export const stats = [
  { value: "79,200+", label: "Trips Completed" },
  { value: "22+", label: "Years of Excellence" },
  { value: "500+", label: "Happy Clients" },
  { value: "100+", label: "Fleet Partners" },
  { value: "Pan India", label: "Delivery" },
] as const;

export const nav = [
  { href: "#top", label: "Home" },
  { href: "#about", label: "About Us" },
  { href: "#materials", label: "Materials" },
  { href: "#plants", label: "Our Plants" },
  { href: "#rates", label: "Live Rates" },
  { href: "#track", label: "Track Order" },
  { href: "#contact", label: "Contact Us" },
] as const;

export const stages = ["Confirmed", "Loading", "On the Way", "Delivered"] as const;

const freight: Record<string, Record<string, number>> = {
  kotputli: {
    "Gurugram, Haryana": 420,
    "Manesar, Haryana": 390,
    "Faridabad, Haryana": 400,
    Delhi: 460,
    "Noida, Uttar Pradesh": 490,
    "Bhiwadi, Rajasthan": 240,
    "Neemrana, Rajasthan": 180,
    "Alwar, Rajasthan": 200,
    "Jaipur, Rajasthan": 260,
  },
  behror: {
    "Gurugram, Haryana": 360,
    "Manesar, Haryana": 330,
    "Faridabad, Haryana": 350,
    Delhi: 400,
    "Noida, Uttar Pradesh": 430,
    "Bhiwadi, Rajasthan": 160,
    "Neemrana, Rajasthan": 120,
    "Alwar, Rajasthan": 180,
    "Jaipur, Rajasthan": 300,
  },
  bhiwadi: {
    "Gurugram, Haryana": 180,
    "Manesar, Haryana": 150,
    "Faridabad, Haryana": 170,
    Delhi: 240,
    "Noida, Uttar Pradesh": 280,
    "Bhiwadi, Rajasthan": 40,
    "Neemrana, Rajasthan": 140,
    "Alwar, Rajasthan": 220,
    "Jaipur, Rajasthan": 360,
  },
};

export function materialById(id: string) {
  return materials.find((item) => item.id === id) ?? materials[0];
}

export function plantById(id: string) {
  return plants.find((item) => item.id === id) ?? plants[0];
}

export function freightFor(plantId: string, delivery: string) {
  return freight[plantId]?.[delivery] ?? 520;
}

export function inr(value: number) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(value);
}
