-- Job details for applicants: responsibilities, requirements and benefits.
-- Table: public.jobs. Reason: applicants should see the duties, qualifications and benefits of a job,
-- and the admin Jobs page should be able to edit them.
-- Format: plain text, one item per line (shown as bullets in the app and admin).
-- Verification: select title, responsibilities, requirements, benefits from public.jobs limit 5;

alter table public.jobs
    add column if not exists responsibilities text,
    add column if not exists requirements text,
    add column if not exists benefits text;

-- Backfill the existing seeded/test jobs by position title. Only rows that are still empty are filled,
-- so anything edited in the admin is left untouched.

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Oversee daily store operations and staff scheduling
Monitor sales targets and implement action plans
Coach and train sales staff on products and service standards
Handle customer escalations and ensure satisfaction
Maintain inventory accuracy and visual merchandising'),
    requirements = coalesce(nullif(requirements, ''), 'Bachelor''s degree in Business or a related field
At least 2 years of supervisory experience in retail
Strong leadership and communication skills
Proficient with POS systems and basic MS Office
Willing to work weekends, holidays and shifting schedules'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Store Supervisor';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Assist the store supervisor in opening and closing procedures
Supervise sales staff and cashiers during shifts
Monitor stock levels and replenish shelves
Resolve customer concerns on the sales floor
Prepare daily sales and attendance reports'),
    requirements = coalesce(nullif(requirements, ''), 'At least 2 years of college or a college graduate
At least 1 year of retail experience
Good communication and people skills
Able to work shifting schedules, weekends and holidays'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Assistant Store Supervisor';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Greet and assist customers in finding products
Maintain product displays and keep shelves stocked
Operate the POS and process payments accurately
Keep the sales floor clean and organized
Support inventory counts and receiving of deliveries'),
    requirements = coalesce(nullif(requirements, ''), 'At least high school graduate; college level is an advantage
Customer service oriented and friendly
No experience required; training provided
Able to stand for long hours and lift light stock
Willing to work shifting schedules, weekends and holidays'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Sales Associate';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Process cash, card and e-wallet payments accurately
Issue receipts and handle returns and exchanges
Balance the cash drawer at the end of each shift
Bag and pack items and assist customers at the counter
Report discrepancies and suspicious transactions'),
    requirements = coalesce(nullif(requirements, ''), 'At least high school graduate; college level preferred
Basic math and cash handling skills
Honest, careful and customer service oriented
Previous cashier or POS experience is an advantage'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Cashier';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Receive and check deliveries against purchase documents
Replenish shelves and keep the stockroom organized
Perform regular stock counts and report discrepancies
Tag and price merchandise
Keep storage and receiving areas clean and safe'),
    requirements = coalesce(nullif(requirements, ''), 'At least high school graduate
Physically fit and able to lift and carry stock
Attentive to detail and organized
Willing to work shifting schedules'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Stock Clerk';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Plan and set up product displays and promotions
Follow brand merchandising guidelines and planograms
Refresh displays regularly based on sales and season
Coordinate with store staff on stock needed for displays
Report display issues and sales impact to management'),
    requirements = coalesce(nullif(requirements, ''), 'College graduate in Marketing, Fine Arts, Design or related field
At least 1 year of experience in visual merchandising or retail
Creative eye and good sense of layout
Willing to travel between branches when needed'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Visual Merchandiser';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Monitor stock levels and run regular cycle counts
Reconcile discrepancies between physical and system stock
Prepare inventory reports and recommend replenishment
Coordinate with warehouse and stores on stock movement
Support audits and shrinkage investigations'),
    requirements = coalesce(nullif(requirements, ''), 'Bachelor''s degree in Business, Accounting, Logistics or related field
At least 1 year of experience in inventory or retail operations
Proficient in MS Excel and inventory systems
Analytical, accurate and detail oriented'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Inventory Analyst';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Oversee receiving, storage and dispatch of inventory
Schedule and supervise warehouse staff
Ensure stock accuracy and safe warehouse practices
Coordinate deliveries with stores and suppliers
Monitor performance and prepare daily operations reports'),
    requirements = coalesce(nullif(requirements, ''), 'Bachelor''s degree or equivalent experience in Logistics or Business
At least 2 years of warehouse experience, with some leadership role
Knowledge of inventory and warehouse management systems
Strong leadership, planning and communication skills'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Warehouse Operations Lead';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Handle customer inquiries, returns and complaints
Process refunds and exchanges following store policy
Answer questions about products and promotions
Log customer feedback and escalate unresolved concerns
Maintain a courteous and professional attitude at all times'),
    requirements = coalesce(nullif(requirements, ''), 'At least 2 years of college; college graduate preferred
Excellent communication skills in English and Filipino
Patient, empathetic and good at problem solving
Basic computer skills'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Customer Service Representative';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Own store sales, profitability and overall performance
Hire, train, schedule and develop store personnel
Ensure compliance with company policies and standards
Manage inventory, costs and shrinkage
Build strong customer relations and a positive store culture'),
    requirements = coalesce(nullif(requirements, ''), 'Bachelor''s degree in Business or a related field
At least 4 years of retail experience, including 2 years in a managerial role
Proven record in sales and people management
Strong leadership, analytical and communication skills
Willing to work weekends, holidays and shifting schedules'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Store Manager';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Monitor store entrances, exits and premises
Prevent theft and report suspicious activity
Inspect bags and assist with loss prevention procedures
Respond to emergencies and follow safety protocols
Maintain a daily logbook of incidents'),
    requirements = coalesce(nullif(requirements, ''), 'High school graduate
Valid security guard license is preferred
Physically fit, alert and disciplined
Willing to work night and rotating shifts'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Security Guard';

update public.jobs set
    responsibilities = coalesce(nullif(responsibilities, ''), 'Keep the store and stockroom clean and safe
Perform basic maintenance and repair of fixtures
Dispose of waste properly and restock supplies
Assist in moving stock and displays when needed
Report damages and safety hazards'),
    requirements = coalesce(nullif(requirements, ''), 'At least high school graduate
Basic knowledge of cleaning and minor repairs
Hardworking, reliable and physically fit
Willing to work shifting schedules'),
    benefits = coalesce(nullif(benefits, ''), 'SSS, PhilHealth and Pag-IBIG contributions
13th month pay and performance bonuses
Employee discount on all products
Paid leaves and holiday pay
Training and career growth opportunities')
where title = 'Utility / Maintenance Staff';

