/** Public lazy page loaders for the sales and customer-service operator module. */
export const operatorWorkbenchPages = {
  inbox: () => import("../inbox/InboxPage").then((module) => ({ default: module.InboxPage })),
  myChannels: () => import("../operator-channel/MyChannelsPage").then((module) => ({ default: module.MyChannelsPage })),
  customers: () => import("../usermgmt/UserManagementPage").then((module) => ({ default: module.UserManagementPage })),
  customerDetail: () => import("../usermgmt/UserDetailPage").then((module) => ({ default: module.UserDetailPage })),
  personas: () => import("../persona/PersonaPage").then((module) => ({ default: module.PersonaPage })),
};
