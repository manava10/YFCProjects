import { create } from "zustand";
import { persist } from "zustand/middleware";

export type Stage = 0 | 1 | 2 | 3;

export type Ticket = {
  id: string;
  materialId: string;
  materialName: string;
  qty: number;
  plantId: string;
  plantName: string;
  delivery: string;
  mediator: string;
  rate: number;
  freight: number;
  createdAt: number;
  stage: Stage;
};

export type Enquiry = {
  id: string;
  name: string;
  phone: string;
  note: string;
  createdAt: number;
};

type DeskState = {
  tickets: Ticket[];
  activeId: string | null;
  enquiries: Enquiry[];
  pickedMaterialId: string | null;
  pickedPlantId: string | null;
  addTicket: (ticket: Ticket) => void;
  setStage: (id: string, stage: Stage) => void;
  setActive: (id: string | null) => void;
  addEnquiry: (enquiry: Enquiry) => void;
  pickMaterial: (id: string) => void;
  pickPlant: (id: string) => void;
};

export const useDesk = create<DeskState>()(
  persist(
    (set) => ({
      tickets: [],
      activeId: null,
      enquiries: [],
      pickedMaterialId: null,
      pickedPlantId: null,
      addTicket: (ticket) =>
        set((state) => ({
          tickets: [ticket, ...state.tickets].slice(0, 24),
          activeId: ticket.id,
        })),
      setStage: (id, stage) =>
        set((state) => ({
          tickets: state.tickets.map((ticket) =>
            ticket.id === id ? { ...ticket, stage } : ticket,
          ),
        })),
      setActive: (id) => set({ activeId: id }),
      addEnquiry: (enquiry) =>
        set((state) => ({ enquiries: [enquiry, ...state.enquiries].slice(0, 12) })),
      pickMaterial: (id) => set({ pickedMaterialId: id }),
      pickPlant: (id) => set({ pickedPlantId: id }),
    }),
    {
      name: "yfc-desk",
      skipHydration: true,
      partialize: (state) => ({
        tickets: state.tickets,
        activeId: state.activeId,
        enquiries: state.enquiries,
        pickedMaterialId: state.pickedMaterialId,
        pickedPlantId: state.pickedPlantId,
      }),
    },
  ),
);

export function nextTicketId() {
  const stamp = Date.now().toString().slice(-4);
  const salt = Math.floor(Math.random() * 10);
  return `YFC-${stamp}${salt}`;
}
